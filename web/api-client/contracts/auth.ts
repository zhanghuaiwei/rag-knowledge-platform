import type { CurrentUser, LoginInput } from "@/api-client/types";

/** 认证域契约。 */
export interface AuthApi {
  getCurrentUser(): Promise<CurrentUser>;
  /** 账号密码登录（form 模式；成功后由后端签发会话 cookie）。 */
  login(input: LoginInput): Promise<CurrentUser>;
}
