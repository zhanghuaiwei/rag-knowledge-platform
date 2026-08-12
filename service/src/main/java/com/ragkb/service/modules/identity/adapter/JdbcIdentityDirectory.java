package com.ragkb.service.modules.identity.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragkb.service.modules.identity.persistence.entity.IdentityAccount;
import com.ragkb.service.modules.identity.persistence.entity.SysUser;
import com.ragkb.service.modules.identity.persistence.entity.UserCredential;
import com.ragkb.service.modules.identity.persistence.mapper.IdentityAccountMapper;
import com.ragkb.service.modules.identity.persistence.mapper.SysUserMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserCredentialMapper;
import com.ragkb.service.modules.identity.persistence.row.TenantMembershipRow;
import com.ragkb.service.modules.identity.port.IdentityDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 数据库身份目录（db.enabled=true 时激活，form 与 oidc 两种模式共用）。
 *
 * <p>把已验证主体的 subjectKey 解析为全局用户与租户成员关系：
 * <ul>
 *   <li>{@code form|<username>}：按登录标识查 {@code user_credential} → {@code sys_user}；</li>
 *   <li>{@code issuer|subject}：按 {@code identity_account} 查 {@code sys_user}（OIDC 读取路径）；</li>
 *   <li>成员关系：{@code tenant_member(status=ACTIVE)} + {@code tenant_member_role} +
 *       {@code sys_tenant}（code/name/policy_version），跨表 JOIN 在 {@code TenantMemberMapper.xml}，
 *       一次取回全部角色避免 N+1。</li>
 * </ul>
 *
 * <p>deny 语义（谁能登录由数据库决定）：主体/凭据/用户/租户任一非 ACTIVE 即返回空 → 401/403。
 *
 * <p>⚠️ 谨慎区（人工复核）：ACTIVE 过滤与 deny 语义、锁定时序与
 * {@code JdbcUserDetailsService}/{@code JdbcUserCredentialStore} 的一致性。
 * 企业 IdP 首次登录自动建号（JIT provisioning）本实现**不做**：新 IdP 用户无
 * {@code identity_account} 记录 → 返回空 → 401，直到管理员开通。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class JdbcIdentityDirectory implements IdentityDirectory {

    private static final String FORM_PREFIX = "form|";

    private final UserCredentialMapper userCredentialMapper;
    private final IdentityAccountMapper identityAccountMapper;
    private final SysUserMapper sysUserMapper;
    private final TenantMemberMapper tenantMemberMapper;

    public JdbcIdentityDirectory(
            UserCredentialMapper userCredentialMapper,
            IdentityAccountMapper identityAccountMapper,
            SysUserMapper sysUserMapper,
            TenantMemberMapper tenantMemberMapper) {
        this.userCredentialMapper = userCredentialMapper;
        this.identityAccountMapper = identityAccountMapper;
        this.sysUserMapper = sysUserMapper;
        this.tenantMemberMapper = tenantMemberMapper;
    }

    @Override
    public Optional<ResolvedIdentity> resolveBySubjectKey(String subjectKey) {
        if (subjectKey == null) {
            return Optional.empty();
        }
        if (subjectKey.startsWith(FORM_PREFIX)) {
            return resolveFormUser(subjectKey.substring(FORM_PREFIX.length()), subjectKey);
        }
        return resolveOidcUser(subjectKey);
    }

    @Override
    public List<TenantMembership> memberships(long userId) {
        List<TenantMembershipRow> rows = tenantMemberMapper.selectActiveMembershipRows(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        // 按租户聚合（每行一个角色）；LinkedHashMap 保持租户出现顺序
        Map<Long, List<TenantMembershipRow>> rowsByTenant = rows.stream().collect(
                Collectors.groupingBy(TenantMembershipRow::getTenantId, LinkedHashMap::new, Collectors.toList()));
        return rowsByTenant.values().stream().map(this::toMembership).toList();
    }

    @Override
    public Optional<TenantMembership> membership(long userId, long tenantId) {
        List<TenantMembershipRow> rows = tenantMemberMapper.selectMembershipRows(userId, tenantId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toMembership(rows));
    }

    // ---------- 内部工具 ----------

    /** form 登录：按登录标识查凭据 → 全局用户。凭据非 ACTIVE（禁用/锁定/不存在）即拒绝。 */
    private Optional<ResolvedIdentity> resolveFormUser(String username, String subjectKey) {
        UserCredential credential = userCredentialMapper.selectOne(
                new LambdaQueryWrapper<UserCredential>()
                        .eq(UserCredential::getUsername, username)
                        .last("LIMIT 1"));
        if (credential == null || !"ACTIVE".equals(credential.getStatus())) {
            return Optional.empty();
        }
        return toResolvedIdentity(credential.getUserId(), subjectKey);
    }

    /** OIDC：按 issuer|subject 查身份绑定 → 全局用户。 */
    private Optional<ResolvedIdentity> resolveOidcUser(String subjectKey) {
        int separator = subjectKey.indexOf('|');
        if (separator <= 0 || separator == subjectKey.length() - 1) {
            return Optional.empty();
        }
        IdentityAccount account = identityAccountMapper.selectOne(
                new LambdaQueryWrapper<IdentityAccount>()
                        .eq(IdentityAccount::getIssuer, subjectKey.substring(0, separator))
                        .eq(IdentityAccount::getSubject, subjectKey.substring(separator + 1))
                        .last("LIMIT 1"));
        if (account == null) {
            return Optional.empty();
        }
        return toResolvedIdentity(account.getUserId(), subjectKey);
    }

    /** 全局用户必须存在且 ACTIVE，否则拒绝。 */
    private Optional<ResolvedIdentity> toResolvedIdentity(long userId, String subjectKey) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedIdentity(userId, subjectKey, user.getDisplayName(), user.getPrimaryEmail()));
    }

    /** 聚合同一租户的多行（每行一个角色）为一条成员关系；租户 ACTIVE 由 SQL 保证。 */
    private TenantMembership toMembership(List<TenantMembershipRow> rows) {
        TenantMembershipRow first = rows.get(0);
        List<String> roles = rows.stream()
                .map(TenantMembershipRow::getRole)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        long policyVersion = first.getPolicyVersion() != null ? first.getPolicyVersion() : 1L;
        return new TenantMembership(
                first.getTenantId(), first.getTenantCode(), first.getTenantName(),
                "ACTIVE", roles, policyVersion);
    }
}
