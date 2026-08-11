/**
 * 前端体验层角色判断（权限矩阵 §3.2 的 UI 表达）。
 *
 * 注意：前端只做显示控制与路由守卫，真正的授权仍由服务端策略决定（GKB-01）；
 * 无权限时统一按 401/403/404 处理，不把"无权限"伪装成"不存在"。
 */

/** 可访问管理中心（成员/组织/API Key/Webhook/审计/标签）的角色。 */
export const ADMIN_ROLES = ["TENANT_ADMIN", "KNOWLEDGE_ADMIN"] as const;

/** 可访问治理中心（审核/元数据/保留/删除）的角色。 */
export const GOVERNANCE_ROLES = ["TENANT_ADMIN", "KNOWLEDGE_ADMIN", "AUDITOR"] as const;

export function hasAnyRole(roles: string[], allowed: readonly string[]): boolean {
  return roles.some((role) => (allowed as readonly string[]).includes(role));
}

export function canAccessAdmin(roles: string[]): boolean {
  return hasAnyRole(roles, ADMIN_ROLES);
}

export function canAccessGovernance(roles: string[]): boolean {
  return hasAnyRole(roles, GOVERNANCE_ROLES);
}

/** 知识库角色等级：OWNER ≥ EDITOR ≥ VIEWER。 */
const KB_ROLE_RANK: Record<string, number> = { VIEWER: 0, EDITOR: 1, OWNER: 2 };

export function kbRoleAtLeast(role: string, min: "OWNER" | "EDITOR" | "VIEWER"): boolean {
  return (KB_ROLE_RANK[role] ?? 0) >= KB_ROLE_RANK[min];
}
