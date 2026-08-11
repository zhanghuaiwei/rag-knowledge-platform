package com.ragkb.service.modules.identity.port;

import java.util.List;
import java.util.Optional;

/**
 * 身份目录：把已验证主体（form dev 用户 / OIDC subject）解析为全局用户与租户成员关系。
 *
 * <p>设计依据（认证授权 §3、§5.1）：租户/角色/组织由服务端从已验证身份推导，不信任客户端自报；
 * 登录、刷新、会话与租户切换都经本端口获取当前真实成员关系，避免长寿命 JWT claim 成为永久权限真相。
 *
 * <p>实现：
 * <ul>
 *   <li>{@link com.ragkb.service.modules.identity.adapter.LocalIdentityDirectory}：form 模式（开发/演示），
 *       内存 dev 用户 → 默认租户；</li>
 *   <li>{@code JdbcIdentityDirectory}（OIDC 生产）：identity_account → sys_user → tenant_member /
 *       tenant_member_role / sys_tenant 映射，⚠️ 人工实现点（谨慎区），本轮仅占位。</li>
 * </ul>
 */
public interface IdentityDirectory {

    /** 全局用户身份（subjectKey 为 issuer+subject 映射键）。 */
    record ResolvedIdentity(long userId, String subjectKey, String displayName, String email) {
    }

    /** 用户在某个租户的成员关系（含角色与策略版本）。 */
    record TenantMembership(long tenantId, String tenantCode, String tenantName,
                            String status, List<String> roles, long policyVersion) {
    }

    /** 按 subjectKey 解析全局用户；未找到返回空（默认拒绝）。 */
    Optional<ResolvedIdentity> resolveBySubjectKey(String subjectKey);

    /** 用户所有 ACTIVE 租户成员关系；非成员返回空列表。 */
    List<TenantMembership> memberships(long userId);

    /** 用户在指定租户的成员关系；非成员/未激活返回空。 */
    Optional<TenantMembership> membership(long userId, long tenantId);
}
