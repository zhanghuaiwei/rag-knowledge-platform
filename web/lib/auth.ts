/**
 * 登录态（mock 演示）：localStorage 持有会话标记。
 *
 * 真实实现为 OIDC Authorization Code + PKCE（GKB-02）：登录后由后端签发
 * HttpOnly Cookie / 内存 access token，前端不持久化凭证；本模块届时仅保留
 * 守卫跳转语义。企业平台不开放自助注册，成员由管理员邀请（管理中心 → 成员与组织）。
 */

export const AUTH_STORAGE_KEY = "ragkb-auth";

export interface AuthSession {
  email: string;
  loginAt: string;
}

export function getSession(): AuthSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuthSession) : null;
  } catch {
    return null;
  }
}

export function isAuthed(): boolean {
  return getSession() !== null;
}

export function setSession(email: string): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ email, loginAt: new Date().toISOString() }));
}

export function clearSession(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}
