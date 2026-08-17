package com.ragkb.service.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.config.LocalAuthProperties;
import com.ragkb.service.modules.identity.dto.CreateLocalUserRequest;
import com.ragkb.service.modules.identity.persistence.entity.SysUser;
import com.ragkb.service.modules.identity.persistence.entity.TenantMember;
import com.ragkb.service.modules.identity.persistence.entity.TenantMemberRole;
import com.ragkb.service.modules.identity.persistence.entity.UserCredential;
import com.ragkb.service.modules.identity.persistence.mapper.SysUserMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberRoleMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserAccountMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserCredentialMapper;
import com.ragkb.service.modules.identity.persistence.query.UserAccountRow;
import com.ragkb.service.modules.identity.persistence.query.UserOrgRow;
import com.ragkb.service.modules.identity.persistence.query.UserRoleRow;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.identity.service.UserAccountService;
import com.ragkb.service.modules.identity.vo.UserVo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link UserAccountService} 实现（identity 模块）。
 *
 * <p>作用域：当前激活租户（{@code JwtPrincipal.tenantId()}）。用户为全局身份，租户关系
 * 在 identity 模块自有表（{@code tenant_member / tenant_member_role / user_credential / sys_user}）。
 *
 * <p>安全边界（写操作统一守卫）：
 * <ul>
 *   <li>资源归属：所有按 userId 的操作先校验目标为当前租户成员；</li>
 *   <li>最后管理员保护：setRoles / removeFromTenant 不得使租户失去最后一名 ACTIVE 的 TENANT_ADMIN；</li>
 *   <li>自我操作保护：不得修改自己的角色、不得把自己移出租户；</li>
 *   <li>密码红线：只存 BCrypt 哈希（{@code PasswordPolicy} 统一强度），日志/审计不落明文；</li>
 *   <li>审计：建号/改角色/移出/重置密码写 {@code audit_log}（SQL 直连，不跨模块依赖 admin 持久化）。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class UserAccountServiceImpl implements UserAccountService {

    /** 租户成员状态（与 init.sql CHECK 一致）；列表展示时映射为 UserVo.status。 */
    private static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    private static final String MEMBER_STATUS_SUSPENDED = "SUSPENDED";

    /** 租户管理员角色码（"最后一名管理员保护"守卫的对象角色）。 */
    private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";

    /** 合法租户角色码字典（对齐 tenant_member_role.role 的 DDL CHECK 与 DTO @Pattern，单一语义三处对齐）。 */
    private static final Set<String> KNOWN_TENANT_ROLES = Set.of(
            "TENANT_ADMIN", "SECURITY_ADMIN", "KNOWLEDGE_ADMIN", "AUDITOR", "MEMBER");

    /** 凭据状态健康值（新建/重置后凭据可用，锁定与失败计数清零）。 */
    private static final String CREDENTIAL_STATUS_ACTIVE = "ACTIVE";

    /** 审计动作码（写入 audit_log.action，命名：<资源>.<动作>）。 */
    private static final String AUDIT_ACTION_CREATE_LOCAL_USER = "tenant_member.create_local";
    private static final String AUDIT_ACTION_SET_ROLES = "tenant_member.set_roles";
    private static final String AUDIT_ACTION_REMOVE_FROM_TENANT = "tenant_member.remove";
    private static final String AUDIT_ACTION_RESET_PASSWORD = "user_credential.reset_password";

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
        // 资源归属校验：目标必须是当前租户成员（非成员时 sys_user_org 的 FK 也会拒绝，这里显式 404）
        long tenantId = currentTenantId();
        requireMemberExists(tenantId, userId);
        // clear-and-set 第一步：清空该成员在当前租户的全部组织关联
        userAccountMapper.deleteMemberOrgs(tenantId, userId);
        // orgId 非 null 时设置新组织；null 表示"移出组织"（仅保留 clear 步）
        if (orgId != null) {
            // 校验目标组织存在于当前租户且 ACTIVE，防止跨租户挂靠或挂到停用组织
            if (userAccountMapper.countOrgInTenant(tenantId, orgId) == 0) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "目标组织不存在或不可用");
            }
            // 写入新组织关联（幂等：撞 (tenant_id,user_id,org_id) 唯一约束时忽略）
            userAccountMapper.insertMemberOrg(tenantId, userId, orgId);
        }
        // 返回更新后的成员视图（重新聚合角色与组织名）
        return loadUserVo(tenantId, userId);
    }

    // ---------- 谨慎区：密码操作/成员移除等安全敏感实现 ----------

    @Override
    @Transactional
    public UserVo createLocalUser(CreateLocalUserRequest request) {
        // 初始密码与自助改密执行同一强度策略（>=8 位且含字母数字），避免策略漂移
        PasswordPolicy.requireStrong(request.password());
        // BCrypt 编码后落库（form 模式才装配 PasswordEncoder，缺失即明确报错）
        String passwordHash = requirePasswordEncoder().encode(request.password());
        long tenantId = currentTenantId();
        Instant now = Instant.now();
        try {
            // 1) 全局身份 sys_user（一人一行；display_name/email 供列表展示）
            SysUser user = new SysUser();
            user.setPrimaryEmail(request.email());          // 全局登录身份邮箱（展示与联络用）
            user.setDisplayName(request.displayName());     // 展示名
            user.setStatus(MEMBER_STATUS_ACTIVE);           // 身份直接可用（凭据另有状态位）
            sysUserMapper.insert(user);                     // 主键回填 user.id
            // 2) 本地登录凭据 user_credential（username 全局唯一 + BCrypt hash + 首登强制改密）
            UserCredential credential = new UserCredential();
            credential.setUserId(user.getId());             // 关联全局身份
            credential.setUsername(request.username());     // 登录标识（lower(username) 部分唯一索引）
            credential.setPasswordHash(passwordHash);       // 只存哈希，永不落明文
            credential.setStatus(CREDENTIAL_STATUS_ACTIVE); // 新凭据可用（无历史锁定/失败）
            credential.setFailedAttempts(0);                // 失败计数从零开始
            credential.setPasswordChangedAt(now);           // 过期判定起点 = 建号时刻
            credential.setPasswordExpiresAt(passwordExpiresAt(now)); // 按策略配置计算；未启用为 null
            credential.setMustChangePassword(true);         // 初始密码一次性：首登强制改密
            userCredentialMapper.insert(credential);
            // 3) 当前租户成员关系 tenant_member（创建者所在租户，状态 ACTIVE）
            TenantMember member = new TenantMember();
            member.setTenantId(tenantId);                   // 从 JWT 推导，不信任客户端自报
            member.setUserId(user.getId());                 // 关联全局身份
            member.setStatus(MEMBER_STATUS_ACTIVE);         // 直接激活（无 INVITED 邀请流）
            member.setJoinedAt(now);                        // 加入时间
            tenantMemberMapper.insert(member);
            // 4) 租户角色 tenant_member_role（每角色一行；去重防重复角色撞唯一约束）
            for (String role : distinctRoles(request.roles())) {
                tenantMemberRoleMapper.insert(memberRoleOf(tenantId, user.getId(), role));
            }
            // 安全审计：记录建号动作（只记 actor/对象 id，不含密码）
            writeAudit(tenantId, AUDIT_ACTION_CREATE_LOCAL_USER, user.getId());
            return loadUserVo(tenantId, user.getId());
        } catch (DuplicateKeyException e) {
            // lower(username) 部分唯一索引冲突：登录账号已被占用 → 业务化为 409
            throw new ApiException(ErrorCode.CONFLICT, "登录账号已存在");
        }
    }

    @Override
    @Transactional
    public UserVo setRoles(long userId, List<String> roles) {
        long tenantId = currentTenantId();
        // 守卫 1：禁止修改自己的角色（防止管理员临时降权自己后无人可管理，也避免自抬/自降的审计歧义）
        if (currentPrincipal().userId() == userId) {
            throw new ApiException(ErrorCode.FORBIDDEN, "不能修改自己的角色");
        }
        // 守卫 2：角色码必须全部合法（空/未知角色 → 400；与 DDL CHECK、DTO @Pattern 对齐的防御性校验）
        Set<String> newRoles = requireKnownRoles(roles);
        // 守卫 3：目标必须是当前租户成员（资源归属校验）
        requireMemberExists(tenantId, userId);
        // 读取目标用户当前角色，供"最后一名管理员保护"判定
        Set<String> oldRoles = currentRolesOf(tenantId, userId);
        if (oldRoles.contains(ROLE_TENANT_ADMIN) && !newRoles.contains(ROLE_TENANT_ADMIN)) {
            // 本次替换会移除目标的 TENANT_ADMIN：若租户内 ACTIVE 管理员仅此一人，则拒绝（防止租户失去管理能力）
            if (userAccountMapper.countActiveTenantAdmins(tenantId) <= 1) {
                throw new ApiException(ErrorCode.FORBIDDEN, "不能移除租户内最后一名 TENANT_ADMIN");
            }
        }
        // 覆盖式替换：先物理清空该成员全部角色行（逻辑删除会占住 uq_tenant_member_role 唯一键）
        tenantMemberRoleMapper.hardDeleteByTenantAndUser(tenantId, userId);
        // 再按入参逐角色插入（newRoles 已去重）
        for (String role : newRoles) {
            tenantMemberRoleMapper.insert(memberRoleOf(tenantId, userId, role));
        }
        // 安全审计：记录角色变更动作（不记角色明细，角色可在用户列表重查）
        writeAudit(tenantId, AUDIT_ACTION_SET_ROLES, userId);
        return loadUserVo(tenantId, userId);
    }

    @Override
    @Transactional
    public void removeFromTenant(long userId) {
        long tenantId = currentTenantId();
        // 守卫 1：禁止把自己移出租户（防止误操作后失去自己的管理入口）
        if (currentPrincipal().userId() == userId) {
            throw new ApiException(ErrorCode.FORBIDDEN, "不能将自己移出租户");
        }
        // 守卫 2：目标必须是当前租户成员（资源归属校验）
        requireMemberExists(tenantId, userId);
        // 守卫 3：最后一名管理员保护 —— 目标是 TENANT_ADMIN 且租户内 ACTIVE 管理员仅此一人时拒绝移出
        if (currentRolesOf(tenantId, userId).contains(ROLE_TENANT_ADMIN)
                && userAccountMapper.countActiveTenantAdmins(tenantId) <= 1) {
            throw new ApiException(ErrorCode.FORBIDDEN, "不能移除租户内最后一名 TENANT_ADMIN");
        }
        // 安全审计：先写审计再删除（同一事务，删除失败回滚时审计一并回滚，不留孤儿审计）
        writeAudit(tenantId, AUDIT_ACTION_REMOVE_FROM_TENANT, userId);
        // 物理删除租户成员关系行；tenant_member_role / sys_user_org 经 FK ON DELETE CASCADE 级联清理。
        // 规则 18 解读：仅清理租户关系行，sys_user / user_credential 全局身份与凭据保留（不属删除用户数据）。
        tenantMemberMapper.hardDeleteByTenantAndUser(tenantId, userId);
    }

    @Override
    public void resetPassword(long userId, String newPassword) {
        long tenantId = currentTenantId();
        // 资源归属校验：只能重置当前租户内的成员（防止跨租户重置他人密码的越权通道）
        requireMemberExists(tenantId, userId);
        // 目标用户的本地登录凭据必须存在（oidc 用户无本地凭据，无法重置）
        UserCredentialStorePort.CredentialRecord credential = credentialStore.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "用户不存在本地登录凭据"));
        // 重置密码与自助改密执行同一强度策略
        PasswordPolicy.requireStrong(newPassword);
        // BCrypt 编码（明文只在本方法内存在，不落日志/审计）
        String passwordHash = requirePasswordEncoder().encode(newPassword);
        Instant now = Instant.now();
        // 重置语义：must_change_password=true（用户下次登录强制改回自己的密码），过期时间按策略重算
        credentialStore.updatePassword(credential.id(), passwordHash, now,
                passwordExpiresAt(now), true);
        // 安全审计：记录重置动作与对象（绝不记录新密码）
        writeAudit(tenantId, AUDIT_ACTION_RESET_PASSWORD, userId);
    }

    @Override
    public Map<Long, String> displayNamesOf(long tenantId, Collection<Long> userIds) {
        // 空集合直接返回，避免无效 IN 查询。
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        // 复用租户成员只读查询（tenant_member × sys_user，天然限定租户 + del_flag=0 过滤）；
        // 未命中租户成员关系的 userId 不会出现在结果里（调用方按缺失即拒绝处理）。
        return userAccountMapper.selectMembers(tenantId, List.copyOf(userIds)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserAccountRow::getUserId,
                        row -> row.getDisplayName() != null ? row.getDisplayName() : "",
                        (first, ignored) -> first));
    }

    // ---------- 内部工具 ----------

    /** 资源归属校验：目标用户必须是当前租户成员，否则 404（所有按 userId 的写操作统一入口）。 */
    private void requireMemberExists(long tenantId, long userId) {
        // 单行存在性查询（tenant_member JOIN sys_user，逻辑删除已过滤）
        if (userAccountMapper.selectMembers(tenantId, List.of(userId)).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "用户不属于当前租户");
        }
    }

    /** 读取成员在当前租户的角色集合（每行一角色的查询结果聚合为 Set）。 */
    private Set<String> currentRolesOf(long tenantId, long userId) {
        Set<String> roles = new LinkedHashSet<>();
        for (UserRoleRow row : userAccountMapper.selectMemberRoles(tenantId, List.of(userId))) {
            roles.add(row.getRole());
        }
        return roles;
    }

    /** 校验入参角色列表非空且全部为已知角色码，去重返回（LinkedSet 保持入参顺序，插入可预期）。 */
    private Set<String> requireKnownRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            // 覆盖式替换不允许清空全部角色（成员至少持有一个角色，与 DTO @NotEmpty 一致）
            throw new ApiException(ErrorCode.BAD_REQUEST, "角色列表不能为空");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String role : roles) {
            if (!KNOWN_TENANT_ROLES.contains(role)) {
                // 未知角色码拒绝（防止越权注入未定义角色绕过权限目录）
                throw new ApiException(ErrorCode.BAD_REQUEST, "未知角色：" + role);
            }
            distinct.add(role);
        }
        return distinct;
    }

    /** 构造一行租户角色记录（tenant_member_role 每角色一行）。 */
    private TenantMemberRole memberRoleOf(long tenantId, long userId, String role) {
        TenantMemberRole memberRole = new TenantMemberRole();
        memberRole.setTenantId(tenantId);   // 角色归属租户（从 JWT 推导）
        memberRole.setUserId(userId);       // 角色归属成员
        memberRole.setRole(role);           // 已校验的合法角色码
        return memberRole;
    }

    /** 建号入参角色去重（防重复角色撞 uq_tenant_member_role 唯一约束）。 */
    private List<String> distinctRoles(List<String> roles) {
        return new ArrayList<>(new LinkedHashSet<>(roles));
    }

    /** 按策略计算密码过期时间：expiryDays>0 返回 now+expiryDays，否则 null（未启用过期）。 */
    private Instant passwordExpiresAt(Instant now) {
        int expiryDays = localAuthProperties.passwordExpiryDays();
        return expiryDays > 0 ? now.plus(Duration.ofDays(expiryDays)) : null;
    }

    /** 写安全审计（actor=当前操作者；只记动作与对象 id，不落密码明文等敏感值）。 */
    private void writeAudit(long tenantId, String action, long targetUserId) {
        userAccountMapper.insertAuditLog(tenantId, currentPrincipal().userId(),
                action, String.valueOf(targetUserId));
    }

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
