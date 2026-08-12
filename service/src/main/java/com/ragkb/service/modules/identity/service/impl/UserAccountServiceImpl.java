package com.ragkb.service.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.config.LocalAuthProperties;
import com.ragkb.service.modules.identity.dto.CreateLocalUserRequest;
import com.ragkb.service.modules.identity.persistence.entity.TenantMember;
import com.ragkb.service.modules.identity.persistence.mapper.SysUserMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberRoleMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserAccountMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserCredentialMapper;
import com.ragkb.service.modules.identity.persistence.row.UserAccountRow;
import com.ragkb.service.modules.identity.persistence.row.UserOrgRow;
import com.ragkb.service.modules.identity.persistence.row.UserRoleRow;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.identity.service.UserAccountService;
import com.ragkb.service.modules.identity.vo.UserVo;
import com.ragkb.service.util.TodoSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link UserAccountService} 实现（identity 模块）。
 *
 * <p>作用域：当前激活租户（{@code JwtPrincipal.tenantId()}）。用户为全局身份，租户关系
 * 在 identity 模块自有表（{@code tenant_member / tenant_member_role / user_credential / sys_user}）。
 *
 * <p>分工：{@code listUsers/disableUser/enableUser} 为机械查询/状态翻转，AI 实现；
 * {@code createLocalUser/setRoles/removeFromTenant/resetPassword} 为谨慎区人工实现点，
 * 本类只提供契约与装配好的依赖，体为 {@code TodoSupport} 占位。
 */
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class UserAccountServiceImpl implements UserAccountService {

    /** 租户成员状态（与 init.sql CHECK 一致）；列表展示时映射为 UserVo.status。 */
    private static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    private static final String MEMBER_STATUS_SUSPENDED = "SUSPENDED";

    private final UserAccountMapper userAccountMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantMemberRoleMapper tenantMemberRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final UserCredentialMapper userCredentialMapper;
    private final UserCredentialStorePort credentialStore;
    private final ObjectProvider<PasswordEncoder> passwordEncoderProvider;
    private final LocalAuthProperties localAuthProperties;

    public UserAccountServiceImpl(
            UserAccountMapper userAccountMapper,
            TenantMemberMapper tenantMemberMapper,
            TenantMemberRoleMapper tenantMemberRoleMapper,
            SysUserMapper sysUserMapper,
            UserCredentialMapper userCredentialMapper,
            UserCredentialStorePort credentialStore,
            ObjectProvider<PasswordEncoder> passwordEncoderProvider,
            LocalAuthProperties localAuthProperties) {
        this.userAccountMapper = userAccountMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.tenantMemberRoleMapper = tenantMemberRoleMapper;
        this.sysUserMapper = sysUserMapper;
        this.userCredentialMapper = userCredentialMapper;
        this.credentialStore = credentialStore;
        this.passwordEncoderProvider = passwordEncoderProvider;
        this.localAuthProperties = localAuthProperties;
    }

    // ---------- AI 实现：查询与状态翻转 ----------

    @Override
    public PageData<UserVo> listUsers(int page, int size) {
        long tenantId = currentTenantId();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        long offset = (long) (safePage - 1) * safeSize;
        List<UserAccountRow> rows = userAccountMapper.selectMemberPage(tenantId, offset, safeSize);
        long total = userAccountMapper.countMembers(tenantId);
        return PageData.of(assembleUserVos(tenantId, rows), total, safePage, safeSize);
    }

    @Override
    public UserVo disableUser(long userId) {
        // ⚠️ 谨慎区（人工加固）：此处应拒绝 ① 停用自己 ② 停用当前租户最后一名 TENANT_ADMIN。
        long tenantId = currentTenantId();
        int updated = tenantMemberMapper.update(null, new LambdaUpdateWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, tenantId)
                .eq(TenantMember::getUserId, userId)
                .set(TenantMember::getStatus, MEMBER_STATUS_SUSPENDED)
                .setSql("suspended_at = now()"));
        if (updated == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "用户不属于当前租户");
        }
        return loadUserVo(tenantId, userId);
    }

    @Override
    public UserVo enableUser(long userId) {
        // ⚠️ 谨慎区（人工加固）：禁止对自己的启用/停用操作留痕时，需校验当前主体（现有实现未做）。
        long tenantId = currentTenantId();
        int updated = tenantMemberMapper.update(null, new LambdaUpdateWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, tenantId)
                .eq(TenantMember::getUserId, userId)
                .set(TenantMember::getStatus, MEMBER_STATUS_ACTIVE)
                .setSql("suspended_at = NULL"));
        if (updated == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "用户不属于当前租户");
        }
        return loadUserVo(tenantId, userId);
    }

    @Override
    @Transactional
    public UserVo updateUserOrg(long userId, Long orgId) {
        // ⚠️ 谨慎区（人工实现）：clear-and-set 单 org ——
        //   1) userAccountMapper.deleteMemberOrgs(currentTenantId(), userId)；          // 清空现有组织关联
        //   2) orgId != null 时：先 userAccountMapper.countOrgInTenant(tenantId, orgId) 校验
        //      组织存在且 ACTIVE（否则 400），再 userAccountMapper.insertMemberOrg(...)；
        //   3) 返回 loadUserVo(tenantId, userId)。
        //   说明：sys_user_org 为 m2m（UNIQUE tenant_id,user_id,org_id），本契约为单 org。
        return TodoSupport.notImplemented("UserAccountService#updateUserOrg");
    }

    // ---------- 谨慎区：人工实现（TodoSupport 占位） ----------

    @Override
    @Transactional
    public UserVo createLocalUser(CreateLocalUserRequest request) {
        // ⚠️ 谨慎区（人工实现）：单事务插入 ——
        //   1) sys_user（primary_email/display_name/status=ACTIVE）；
        //   2) user_credential（username + password_hash = requirePasswordEncoder().encode(request.password())，
        //      status=ACTIVE, must_change_password=true, password_expires_at=now()+expiryDays）；
        //   3) tenant_member（tenantId=currentTenantId(), status=ACTIVE）；
        //   4) 每个角色插入 tenant_member_role 一行。
        //   用户名冲突：捕获部分唯一索引 DuplicateKeyException → ApiException(CONFLICT, "登录账号已存在")。
        return TodoSupport.notImplemented("UserAccountService#createLocalUser");
    }

    @Override
    @Transactional
    public UserVo setRoles(long userId, List<String> roles) {
        // ⚠️ 谨慎区（人工实现）：覆盖式替换 —— 删除该 (tenantId,userId) 全部角色行后按 roles 重新插入；
        //   守卫：不得移除当前租户最后一名 TENANT_ADMIN、不得修改自己的角色；空/未知角色 → 400。
        return TodoSupport.notImplemented("UserAccountService#setRoles");
    }

    @Override
    @Transactional
    public void removeFromTenant(long userId) {
        // ⚠️ 谨慎区（人工实现）：守卫（不得移出最后一名 TENANT_ADMIN / 不得移出自己）后调用
        //   tenantMemberMapper.hardDeleteByTenantAndUser(currentTenantId(), userId)
        //   （FK ON DELETE CASCADE 级联清理角色/组织关联），并写 audit_log。
        //   规则 18 解读：仅租户关系行清理，sys_user / user_credential 全局身份保留（不属删除用户数据）。
        TodoSupport.notImplemented("UserAccountService#removeFromTenant");
    }

    @Override
    public void resetPassword(long userId, String newPassword) {
        // ⚠️ 谨慎区（人工实现）：
        //   UserCredentialStorePort.CredentialRecord credential =
        //       credentialStore.findByUserId(userId).orElseThrow(() -> ApiException(NOT_FOUND, "用户不存在"))；
        //   String hash = requirePasswordEncoder().encode(newPassword)；
        //   credentialStore.updatePassword(credential.id(), hash, Instant.now(),
        //       Instant.now().plus(Duration.ofDays(localAuthProperties.passwordExpiryDays())), true)；
        //   可选：吊销该用户 refresh 家族；写 audit_log。
        TodoSupport.notImplemented("UserAccountService#resetPassword");
    }

    // ---------- 内部工具 ----------

    private UserVo loadUserVo(long tenantId, long userId) {
        List<UserAccountRow> rows = userAccountMapper.selectMembers(tenantId, List.of(userId));
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "用户不属于当前租户");
        }
        return assembleUserVos(tenantId, rows).get(0);
    }

    private List<UserVo> assembleUserVos(long tenantId, List<UserAccountRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = rows.stream().map(UserAccountRow::getUserId).toList();
        Map<Long, List<String>> rolesByUser = new HashMap<>();
        for (UserRoleRow row : userAccountMapper.selectMemberRoles(tenantId, userIds)) {
            rolesByUser.computeIfAbsent(row.getUserId(), k -> new ArrayList<>()).add(row.getRole());
        }
        Map<Long, String> orgByUser = new HashMap<>();
        for (UserOrgRow row : userAccountMapper.selectMemberOrgs(tenantId, userIds)) {
            // 多组织（m2m）取第一个展示用；完整列表属后续迭代
            orgByUser.putIfAbsent(row.getUserId(), row.getOrgName());
        }
        return rows.stream().map(row -> toUserVo(row,
                rolesByUser.getOrDefault(row.getUserId(), List.of()),
                orgByUser.get(row.getUserId()))).toList();
    }

    private UserVo toUserVo(UserAccountRow row, List<String> roles, String orgName) {
        String status = MEMBER_STATUS_SUSPENDED.equals(row.getMemberStatus())
                ? "DISABLED" : row.getMemberStatus();
        return new UserVo(
                row.getUserId(),
                row.getDisplayName() != null ? row.getDisplayName() : "",
                row.getPrimaryEmail() != null ? row.getPrimaryEmail() : "",
                status != null ? status : "DISABLED",
                roles,
                orgName,
                Boolean.TRUE.equals(row.getMustChangePassword()),
                row.getLastLoginAt());
    }

    private PasswordEncoder requirePasswordEncoder() {
        PasswordEncoder encoder = passwordEncoderProvider.getIfAvailable();
        if (encoder == null) {
            // 无 PasswordEncoder 说明非 form 模式（oidc 部署），本地账号功能不可用
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "本地账号管理需要 form 登录模式");
        }
        return encoder;
    }

    private long currentTenantId() {
        return currentPrincipal().tenantId();
    }

    private TokenService.JwtPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
        }
        return principal;
    }
}
