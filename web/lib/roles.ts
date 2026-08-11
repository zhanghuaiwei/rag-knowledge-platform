/**
 * 前端权限判断 helper（权限矩阵的 UI 表达）。
 *
 * 菜单、路由守卫与按钮只使用稳定的 permission 码（见 lib/permissions.ts），
 * 不直接依赖租户角色；真正的服务端授权由后端策略决定（GKB-01），
 * 前端只做显示控制，无权限统一按 401/403/404 处理。
 */

/** 是否具备单个权限；未知权限一律 false（默认拒绝，不宽松回退）。 */
export function can(permissions: string[], permission: string): boolean {
  return permissions.includes(permission);
}

/** 是否具备全部所需权限。 */
export function canAll(permissions: string[], required: readonly string[]): boolean {
  return required.every((permission) => permissions.includes(permission));
}

/** 是否具备任一权限。 */
export function canAny(permissions: string[], allowed: readonly string[]): boolean {
  return allowed.some((permission) => permissions.includes(permission));
}

/**
 * 知识库角色等级：OWNER ≥ EDITOR ≥ VIEWER。
 * ⚠️ 未知角色默认拒绝（不按 VIEWER 宽松回退，见 dynamic-menu §10 P1.3）。
 */
const KB_ROLE_RANK: Record<string, number> = { VIEWER: 0, EDITOR: 1, OWNER: 2 };

export function kbRoleAtLeast(role: string | undefined, min: "OWNER" | "EDITOR" | "VIEWER"): boolean {
  if (!role) return false;
  return (KB_ROLE_RANK[role] ?? -1) >= KB_ROLE_RANK[min];
}
