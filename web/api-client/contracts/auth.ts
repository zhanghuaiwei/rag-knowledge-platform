import type { ChangePasswordInput, CurrentUser, LoginInput } from "@/api-client/types";

/** 认证域契约。 */
export interface AuthApi {
  getCurrentUser(): Promise<CurrentUser>;
  /** 账号密码登录（form 模式）：成功后由 transport 持有 access token，refresh 凭证走 HttpOnly cookie。 */
  login(input: LoginInput): Promise<CurrentUser>;
  /** 切换激活租户：服务端校验成员关系并重签 token，返回新会话上下文。 */
  switchTenant(tenantId: number): Promise<CurrentUser>;
  /** 登出：吊销后端会话（黑名单 access + 吊销 refresh 家族）并清理本地内存 token。 */
  logout(): Promise<void>;
  /** V0.5：自助修改当前用户密码（核验当前密码，清除 mustChangePassword）。 */
  changePassword(input: ChangePasswordInput): Promise<void>;
}
