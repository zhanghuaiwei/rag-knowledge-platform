/** 通用分页与主体类型。 */

export interface PageParams {
  page?: number;
  size?: number;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  hasMore: boolean;
}

export interface CurrentUser {
  id: number;
  name: string;
  email: string;
  tenantId: number;
  tenantName: string;
  /** 当前租户角色（解释身份用，不直接散落到菜单判断）。 */
  tenantRoles: string[];
  /** 凭证能力（如 web / API Key scope），不等于租户角色与最终权限。 */
  credentialScopes: string[];
  /** 菜单/路由/按钮使用的稳定能力码（服务端集中聚合）。 */
  permissions: string[];
  /** 租户套餐、部署模式或后端能力是否可用。 */
  features: string[];
  /** 授权策略版本，用于判断缓存上下文是否过期。 */
  policyVersion: number;
  /** 兼容展示字段：等于 tenantRoles（菜单/守卫请使用 permissions）。 */
  roles: string[];
  /** 可切换租户列表（租户切换下拉用）。 */
  tenants: { tenantId: number; tenantName: string; tenantRoles: string[] }[];
  orgName: string;
  /** V0.5 本地账号：首登/被重置后须改密（前端据此重定向改密页）。 */
  mustChangePassword?: boolean;
}

/** 自助修改密码入参（对齐 OpenAPI ChangePasswordRequest）。 */
export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

/**
 * 表单登录入参。username 为登录标识（本地账号体系的用户名/邮箱），能否登录由后端数据库
 * （user_credential/sys_user）决定；生产 OIDC 由企业 IdP 承载认证。
 */
export interface LoginInput {
  username: string;
  password: string;
}
