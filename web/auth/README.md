# auth

认证相关：OIDC Authorization Code + PKCE 前端处理、会话状态。

- 脚手架阶段：空占位。
- v0.2 采用 BFF + Secure/HttpOnly/SameSite cookie，浏览器不保存长期 token（02-概要设计 §4.1）。
- 边界（BFF vs 纯 SPA）待 Web/安全评审冻结，见 `05-技术选型 §6`。
