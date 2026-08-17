# auth

认证相关：JWT access token（前端内存）+ refresh token（HttpOnly cookie）与会话状态。

- **form 模式（开发/演示）**：`lib/auth.ts` 持有 access token（**仅内存，不落 localStorage**）；
  refresh 凭证由后端写 HttpOnly cookie `ragkb_refresh`（SameSite=Lax, Path=/api/v1/auth），
  JS 无法读取；请求走 `Authorization: Bearer`，401 时经 `POST /api/v1/auth/refresh` 单飞刷新并
  重试一次（见 `api-client/http/client.ts`）。
- **oidc 模式（生产）**：BFF + Secure/HttpOnly/SameSite 会话 cookie，浏览器不保存长期 token
  （02-概要设计 §4.1）；现状未动。
- 登出：`POST /api/v1/auth/logout`（黑名单 access jti + 吊销 refresh 家族）+ 清本地内存 token。
- 后端 JWT 签发/校验与 Redis 黑名单为人工实现点（`TokenService` / 两个 Adapter），实现完成前
  form 模式登录返回 501/500；前端已移除 mock，统一走真实 HTTP（`NEXT_PUBLIC_API_BASE_URL`）。
