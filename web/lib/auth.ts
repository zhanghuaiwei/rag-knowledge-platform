/**
 * 登录态（JWT）：access token 仅存 React 内存，不落 localStorage/sessionStorage。
 *
 * refresh token 由后端写进 HttpOnly cookie（ragkb_refresh，SameSite=Lax，
 * Path=/api/v1/auth），JS 无法读取；刷新走 POST /api/v1/auth/refresh。
 * 页面刷新后内存 token 丢失，由 auth-gate 启动时经 refresh 自动恢复会话。
 * ⚠️ 本模块禁止在服务端组件 / RSC 边界调用（SSR 恒为未登录）。
 */

// 模块级内存变量：仅运行时持有
let accessToken: string | null = null;
let expiresAt: number | null = null; // epoch ms

/** 写入 access token（登录 / 刷新成功时由 api-client transport 调用）。 */
export function setAuth(token: string, expiresInSeconds: number): void {
  accessToken = token;
  expiresAt = Date.now() + expiresInSeconds * 1000;
}

/** 当前有效 access token；未设置或已过期返回 null。 */
export function getAccessToken(): string | null {
  if (!accessToken || expiresAt == null || Date.now() >= expiresAt) return null;
  return accessToken;
}

/** 是否已登录（存在未过期 access token）。 */
export function isAuthed(): boolean {
  return getAccessToken() !== null;
}

/** 清空内存 token（登出 / 刷新失败）。 */
export function clearSession(): void {
  accessToken = null;
  expiresAt = null;
}
