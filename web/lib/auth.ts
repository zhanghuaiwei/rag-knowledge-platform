/**
 * 登录态（开发阶段 mock）：localStorage 持有会话标记。
 *
 * 开发阶段保留表单登录,供本地与演示环境使用;真实实现为 OIDC Authorization
 * Code + PKCE(GKB-02)：登录后由后端签发 HttpOnly Cookie / 内存 access token,
 * 前端不持久化凭证,届时本模块仅保留守卫跳转语义。
 *
 * 认证边界：表单登录仅用于开发/演示环境,生产环境不暴露;企业平台不开放自助
 * 注册,成员由管理员邀请(管理中心 → 成员与组织)。
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
