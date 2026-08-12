/**
 * 认证域真实 HTTP transport：
 * - login ← POST /auth/login（响应含 access token + session；refresh 凭证写 HttpOnly cookie）
 * - getCurrentUser ← GET /auth/session（无 token 时先经 refresh 恢复会话）
 * - switchTenant ← POST /auth/tenant/switch（JWT 模式服务端重签，返回新 TokenResponse）
 * - logout ← POST /auth/logout（黑名单 access + 吊销 refresh 家族 + 清本地 token）
 *
 * access token 只进内存（lib/auth），不落 localStorage。
 */
import type { AuthApi } from "@/api-client/contracts/auth";
import type { ChangePasswordInput, CurrentUser, LoginInput } from "@/api-client/types";
import { request, requestVoid, tryRefreshTokens } from "@/api-client/http/client";
import { clearSession, getAccessToken, setAuth } from "@/lib/auth";

/** 后端 AuthSession 形状（OpenAPI components/schemas/AuthSession，v0.2 权限上下文待评审）。 */
interface AuthSession {
  userId: number;
  subjectKey: string;
  displayName: string;
  activeTenant?: { tenantId: number; tenantCode: string; tenantRoles?: string[] } | null;
  tenants?: { tenantId: number; tenantCode: string; tenantRoles?: string[] }[];
  tenantRoles?: string[];
  credentialScopes?: string[];
  permissions?: string[];
  features?: string[];
  policyVersion?: number;
  /** V0.5 本地账号（form+db）：首登/被重置后须改密。 */
  mustChangePassword?: boolean | null;
  passwordExpired?: boolean | null;
}

/** 登录/刷新/切租户响应（OpenAPI components/schemas/TokenResponse）。 */
interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  session: AuthSession;
}

function mapCurrentUser(session: AuthSession): CurrentUser {
  const activeTenant = session.activeTenant;
  const tenantRoles = activeTenant?.tenantRoles ?? session.tenantRoles ?? [];
  return {
    id: session.userId,
    name: session.displayName,
    email: "", // AuthSession 不含邮箱，会话扩展后由服务端补充
    tenantId: activeTenant?.tenantId ?? 0,
    tenantName: activeTenant?.tenantCode ?? "",
    tenantRoles,
    credentialScopes: session.credentialScopes ?? [],
    permissions: session.permissions ?? [],
    features: session.features ?? [],
    policyVersion: session.policyVersion ?? 0,
    roles: tenantRoles, // 兼容展示字段
    tenants: (session.tenants ?? []).map((tenant) => ({
      tenantId: tenant.tenantId,
      tenantName: tenant.tenantCode,
      tenantRoles: tenant.tenantRoles ?? [],
    })),
    orgName: "", // 组织归属由服务端会话扩展后补充
    mustChangePassword: session.mustChangePassword ?? false,
  };
}

export const authApi: AuthApi = {
  async getCurrentUser() {
    if (!getAccessToken()) {
      const ok = await tryRefreshTokens();
      if (!ok) throw new Error("登录已过期，请重新登录");
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

  /** 切换激活租户（JWT 模式）：服务端校验成员关系并重签，本地替换新 access token。 */
  async switchTenant(tenantId: number) {
    const result = await request<TokenResponse>({
      method: "POST",
      url: "/auth/tenant/switch",
      data: { tenantId },
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

  /** V0.5：自助修改当前用户密码（核验当前密码，成功后清除 mustChangePassword 标志）。 */
  async changePassword(input: ChangePasswordInput) {
    await requestVoid({
      method: "POST",
      url: "/auth/change-password",
      data: { currentPassword: input.currentPassword, newPassword: input.newPassword },
    });
  },
};
