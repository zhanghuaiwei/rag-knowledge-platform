/**
 * 认证域真实 HTTP transport：
 * - login ← POST /auth/login（响应含 access token + session；refresh 凭证写 HttpOnly cookie）
 * - getCurrentUser ← GET /auth/session（无 token 时先经 refresh 恢复会话）
 * - logout ← POST /auth/logout（黑名单 access + 吊销 refresh 家族 + 清本地 token）
 *
 * access token 只进内存（lib/auth），不落 localStorage。
 */
import type { AuthApi } from "@/api-client/contracts/auth";
import type { CurrentUser, LoginInput } from "@/api-client/types";
import { ApiError } from "@/api-client/http/errors";
import { request, requestVoid, tryRefreshTokens } from "@/api-client/http/client";
import { clearSession, getAccessToken, setAuth } from "@/lib/auth";

/** 后端 AuthSession 形状（OpenAPI components/schemas/AuthSession）。 */
interface AuthSession {
  userId: number;
  subjectKey: string;
  displayName: string;
  activeTenant?: { tenantId: number; tenantCode: string; tenantRole: string } | null;
  tenants?: { tenantId: number; tenantCode: string; tenantRole: string }[];
  scopes?: string[];
}

/** 登录/刷新响应（OpenAPI components/schemas/TokenResponse）。 */
interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  session: AuthSession;
}

function mapCurrentUser(session: AuthSession): CurrentUser {
  const activeTenant = session.activeTenant;
  return {
    id: session.userId,
    name: session.displayName,
    email: "", // AuthSession 不含邮箱，会话扩展后由服务端补充
    tenantId: activeTenant?.tenantId ?? 0,
    tenantName: activeTenant?.tenantCode ?? "",
    roles: session.scopes ?? [],
    orgName: "", // 组织归属由服务端会话扩展后补充
  };
}

export const authApi: AuthApi = {
  async getCurrentUser() {
    if (!getAccessToken()) {
      const ok = await tryRefreshTokens();
      if (!ok) throw new ApiError("登录已过期，请重新登录", { status: 401, code: "E-1001" });
    }
    const session = await request<AuthSession>({ method: "GET", url: "/auth/session" });
    return mapCurrentUser(session);
  },

  /** 账号密码登录（form 模式）：成功后本地持有 access token，refresh 凭证由后端写 HttpOnly cookie。 */
  async login(input: LoginInput) {
    const result = await request<TokenResponse>({
      method: "POST",
      url: "/auth/login",
      data: { username: input.username, password: input.password },
    });
    setAuth(result.accessToken, result.expiresIn);
    return mapCurrentUser(result.session);
  },

  /** 登出：吊销后端会话（黑名单 + 吊销 refresh 家族），无论成败都清理本地 token。 */
  async logout() {
    try {
      await requestVoid({ method: "POST", url: "/auth/logout" });
    } finally {
      clearSession();
    }
  },
};
