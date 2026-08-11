/**
 * 认证域真实 HTTP transport：`getCurrentUser` ← GET /auth/session。
 * 后端返回 AuthSession，映射为前端 CurrentUser。
 */
import type { AuthApi } from "@/api-client/contracts/auth";
import type { CurrentUser, LoginInput } from "@/api-client/types";
import { request } from "@/api-client/http/client";

/** 后端 AuthSession 形状（OpenAPI components/schemas/AuthSession）。 */
interface AuthSession {
  userId: number;
  subjectKey: string;
  displayName: string;
  activeTenant?: { tenantId: number; tenantCode: string; tenantRole: string } | null;
  tenants?: { tenantId: number; tenantCode: string; tenantRole: string }[];
  scopes?: string[];
}

function mapCurrentUser(session: AuthSession): CurrentUser {
  const activeTenant = session.activeTenant;
  return {
    id: session.userId,
    name: session.displayName,
    email: "", // AuthSession 不含邮箱，BFF 会话扩展后由服务端补充
    tenantId: activeTenant?.tenantId ?? 0,
    tenantName: activeTenant?.tenantCode ?? "",
    roles: session.scopes ?? [],
    orgName: "", // 组织归属由服务端会话扩展后补充
  };
}

export const authApi: AuthApi = {
  async getCurrentUser() {
    const session = await request<AuthSession>({ method: "GET", url: "/auth/session" });
    return mapCurrentUser(session);
  },

  /** 账号密码登录（form 模式）：成功后后端签发会话 cookie，后续请求自动携带。 */
  async login(input: LoginInput) {
    const session = await request<AuthSession>({
      method: "POST",
      url: "/auth/login",
      data: { username: input.username, password: input.password },
    });
    return mapCurrentUser(session);
  },
};
