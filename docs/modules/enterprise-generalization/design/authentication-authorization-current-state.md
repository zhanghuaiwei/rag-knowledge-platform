# 认证授权体系现状（当前实现盘点）

> **文档状态**：现状梳理 · **对应代码基线**：V0.4/V0.5（本地用户凭据与账号体系落地后） · **最近更新**：2026-08-12
> **定位**：本文只描述**当前已落地的实现**，是 [认证与授权技术方案](authentication-authorization.md)（目标方案）的“现状对照”。两文档不一致时，**已实现/未实现以本文为准**；设计意图仍以原方案为准。
> **契约依据**：[`docs/api/server.openapi.yaml`](../../../api/server.openapi.yaml)
> **数据依据**：[`init.sql`](../../../../deploy/ddl/init.sql)、[`migrations/V0.4__local_user_credentials.sql`](../../../../deploy/ddl/migrations/V0.4__local_user_credentials.sql)、[`V0.5__tenant_accounts.sql`](../../../../deploy/ddl/migrations/V0.5__tenant_accounts.sql)

---

## 1. 一句话结论

认证链路（form 账号密码 + JWT access/refresh + Redis 轮换/黑名单）**已真实实现并接线**，但凭据策略写路径（失败锁定）与**资源级授权（AccessPolicyUseCase）尚未接到任何业务端点**；账号管理的写操作（建号/改角色/重置密码）为占位。整体仍是“可登录、可登出、可切租户”的开发用认证闭环，离生产授权闭环还差“授权接入”这一步。

## 2. 当前实现总览

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| form 账号密码登录（内存 dev / 数据库凭据） | ✅ 已接线 | `SecurityConfig` + `JwtAuthenticationFilter` + `AuthServiceImpl` |
| JWT 签发/校验（access + refresh，HS256） | ✅ 已实现 | `TokenServiceImpl`（非 TODO） |
| refresh 轮换 + 复用检测（Redis Lua CAS） | ✅ 已实现 | `RedisRefreshTokenStoreAdapter` |
| access 登出黑名单（Redis） | ✅ 已实现 | `RedisTokenBlacklistAdapter` |
| 租户切换（重签令牌） | ✅ 已实现 | `AuthServiceImpl.switchTenant` |
| 凭据策略**门禁**（首登/过期强制改密） | ✅ 已接线 | `CredentialPolicyGateFilter` 每请求重读 |
| 凭据策略**记账**（失败锁定/成功重置） | ❌ 未接线 | `CredentialPolicyEventListener` 方法体为空 |
| 自助改密 | ❌ 占位 | `AuthService.changePassword` → 501 |
| 账号管理查询/停用/启用 | ✅ 已实现 | `UserAccountServiceImpl.listUsers/disableUser/enableUser` |
| 账号管理建号/改角色/移出/重置密码/组织 | ❌ 占位 | `TodoSupport` 占位 |
| 方法级授权 `@PreAuthorize` | ✅ 已用 | `UserAccountController`/`ApiKeyController`/`AdminController` |
| 角色→权限目录（PermissionCatalog） | ✅ 已接线 | JWT 过滤器与登录会话均用它展开 |
| 文档级资源授权（AccessPolicyUseCase） | ⚠️ 已实现但**无调用方** | `AccessPolicyServiceImpl` 逻辑完整，未接入端点 |
| API Key（创建/校验/吊销/轮换） | ✅ 已实现 | `ApiKeyCrypto` + `ApiKeyDbStore` + `ApiKeyAuthenticationFilter` |
| API Key 速率限制 | ⚠️ 仅存配置 | `rate_limit_per_minute` 落库，未做限流执行 |
| OIDC 生产登录 | ⚠️ 骨架 | 框架链 + ClientRegistration 已接，JIT 建号/CSRF/Session 生产化未完成 |
| 审计落库（audit_log） | ❌ 未接线 | 表/实体/Mapper 存在，无写入 |
| 前端登录态 + 单飞刷新 + 路由/权限门控 | ✅ 已实现 | `web`（Next.js） |

## 3. 认证模式与身份源

由 `ragkb.auth.mode` 环境变量切换，默认 `form`（`service/src/main/resources/application.yml:74`）。

### 3.1 form 模式（开发/演示默认，账号密码）

装配于 `ragkb.auth.mode=form`（`matchIfMissing=true`，`service/src/main/java/com/ragkb/service/config/SecurityConfig.java:85-86`）。身份源分两个变体：

| 变体 | 触发条件 | 身份源 | 代码 |
| --- | --- | --- | --- |
| form + 无库 | `ragkb.db.enabled=false`（默认） | 内存 dev 账号 `admin/admin123/TENANT_ADMIN`（`RAGKB_DEV_*`） | `SecurityConfig.java:117-130`（`InMemoryUserDetailsManager`）+ `LocalIdentityDirectory.java:22-69`（固定映射 userId=1、租户 id=1/code=default） |
| form + 有库 | `ragkb.db.enabled=true` | `user_credential` + `sys_user` + `tenant_member(_role)` | `JdbcUserDetailsService.java:31-66` + `JdbcIdentityDirectory.java:43-149` |

条件类定义见 `service/src/main/java/com/ragkb/service/config/IdentityConditions.java:20-49`（`NoDbFormMode` / `DbFormMode`）。

### 3.2 oidc 模式（生产目标，企业 IdP）

- `ragkb.auth.mode=oidc` 时装配（`SecurityConfig.java:144-182`）：`ClientRegistrationRepository` 从 `issuer-uri` 解析 OIDC 发现文档（`ClientRegistrations.fromIssuerLocation`），scope `openid/profile/email`（`SecurityConfig.java:154-159`）。
- BFF Session：`HttpSessionSecurityContextRepository`（`SecurityConfig.java:63-65`），cookie 为框架默认 `JSESSIONID`。
- 回调走 Spring Security `oauth2Login`；`AuthController` 的自定义 `/api/v1/auth/callback`（`AuthController.java:69-73`）调用 `authService.handleCallback`，后者**为空实现**（`AuthServiceImpl.java:104-106`）。
- **JIT provisioning 未实现**：新 IdP 用户无 `identity_account` 记录即返回空 → 401（`JdbcIdentityDirectory.java:111-125`、类注释 39-40）。
- **CSRF 未启用**（`SecurityConfig.java:170`）、Session Store 未生产化（单实例内存 Session）。

### 3.3 API Key（机器访问凭证）

叠加在 `ragkb.db.enabled=true`，不是独立 mode。`ApiKeyAuthenticationFilter`（`service/src/main/java/com/ragkb/service/config/ApiKeyAuthenticationFilter.java:34-36`）处理 `rk_` 前缀的 Bearer token。

## 4. 身份数据模型（表）

全局身份与租户关系分离：**`sys_user`/`user_credential`/`identity_account` 是全局表（无 tenant_id）**，租户归属只存在于 `tenant_member`。表结构依据 `deploy/ddl/init.sql`。

| 表 | 作用 | 关键约束 |
| --- | --- | --- |
| `sys_tenant` | 租户 | `code` 唯一；`policy_version`（授权版本号）`init.sql:123-139` |
| `sys_user` | 全局身份唯一真相 | 无租户列；`lower(primary_email)` 部分唯一索引 `init.sql:141-153` |
| `identity_account` | IdP 身份绑定（OIDC/SAML） | `UNIQUE (issuer, subject)`；protocol 枚举 `init.sql:155-168` |
| `identity_provider` | 租户级 IdP 配置 | 带 `tenant_id`；无业务用例 `init.sql:170-191` |
| `tenant_member` | 用户↔租户成员关系 | `UNIQUE (tenant_id, user_id)`；status `INVITED/ACTIVE/SUSPENDED` `init.sql:193-207` |
| `tenant_member_role` | 租户角色 | 复合 FK 级联；`UNIQUE (tenant_id,user_id,role)`；role 枚举 `TENANT_ADMIN/SECURITY_ADMIN/KNOWLEDGE_ADMIN/AUDITOR/MEMBER` `init.sql:209-222` |
| `sys_org` / `sys_user_org` | 组织 / 用户↔组织 m2m | m2m 带 `source`；级联 FK `init.sql:224-265` |
| `kb_member` | KB 内角色 | role `OWNER/EDITOR/VIEWER` `init.sql:358-377` |
| `document_acl` | 文档 ACL | principal `USER/ORG/TENANT_ROLE/KB_ROLE`；permission `VIEW_EXCERPT/VIEW_CONTENT/DOWNLOAD_ORIGINAL` `init.sql:663-681` |
| `api_key` / `api_key_kb` | 机器凭证 / 关联 KB | 只存 `key_digest`(sha256 hex) + `key_prefix`；status `ACTIVE/REVOKED/EXPIRED` `init.sql:1047-1088` |
| `user_credential` | 本地登录凭据（V0.4/V0.5 新增） | BCrypt hash + 凭据策略列；`lower(username)` 部分唯一索引 `V0.4:35-58`、`V0.5:39-57` |
| `audit_log` | 审计 | actor/result/reason/policy_version 等；追加写 `init.sql:1112-1137` |
| `idempotency_record` | 幂等 | 表存在，当前幂等为进程内 map `init.sql:1090-1109` |

**`user_credential` 关键列**（`V0.4:35-58`、`V0.5:39-57`）：`user_id`(FK→sys_user)、`username`、`password_hash`(BCrypt)、`status(ACTIVE/DISABLED/LOCKED)`、`failed_attempts`、`locked_until`、`password_changed_at`、`password_expires_at`、`must_change_password`。**无 pepper**（pepper 仅用于 API Key 摘要）。bootstrap 管理员 seed：`admin/admin123`，租户 1 `TENANT_ADMIN`（`V0.5:63-79`）。

> ⚠️ **迁移顺序约束**：V0.5 把 V0.4 的 `uq_user_credential_username` 唯一约束替换为同名的**部分唯一索引**。V0.4 在 V0.5 之后重跑会在 INSERT 处报 “constraint does not exist”，必须按版本顺序执行（`V0.5:11-16`、`54-57`）。

## 5. form 登录完整链路

### 5.1 登录（POST /api/v1/auth/login）

1. `AuthController.login`（`service/src/main/java/com/ragkb/service/modules/identity/controller/AuthController.java:76-86`）：`AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(...))` 完成用户名/密码校验，随后 `authService.login(authentication)`，并把 refresh token 写入 HttpOnly cookie。
2. 密码校验：`JdbcUserDetailsService.loadUserByUsername` 从 `user_credential` 加载 hash + 状态门禁（`JdbcUserDetailsService.java:42-53`；authorities 刻意留空，`:50-51`），`DaoAuthenticationProvider` + `BCryptPasswordEncoder` 校验（`SecurityConfig.java:132-136`）。
3. `AuthServiceImpl.login`（`AuthServiceImpl.java:119-123`）：`subjectKey = "form|<username>"`（`resolveSubjectKey`，`:363-376`）→ `identityDirectory.resolveBySubjectKey`（`JdbcIdentityDirectory.java:99-108`：凭据 + `sys_user` 必须 ACTIVE）→ 取第一个 ACTIVE 租户成员（`:351-356`）→ 签发令牌 + 保存 refresh family（`issueAuthResult`，`:256-263`）。
4. 响应：`TokenResponseVo`（access token + Bearer + expiresIn + session 视图）放响应体；refresh token 写 `ragkb_refresh` HttpOnly cookie（`AuthController.java:155-164`）。

### 5.2 刷新（POST /api/v1/auth/refresh）

`AuthServiceImpl.refresh`（`AuthServiceImpl.java:126-143`）：解析 refresh token → `verifyAndRotate(familyId, presentedJti, newJti, refreshTtl)` → 失败抛 401（复用即吊销整族）→ 从身份目录**重取真实成员关系**（校验租户成员未变，否则 403）→ `issueRotated` 重签。**角色不固化在 refresh 里，每次轮换重新解析**（`TokenService.java:39-41`）。

### 5.3 登出（POST /api/v1/auth/logout）

`AuthServiceImpl.logout`（`AuthServiceImpl.java:146-160`）：access jti 宽容读取（过期也能取，防重放）加入黑名单（TTL=accessTtl）→ 吊销 refresh 家族 → 清 cookie。幂等。

### 5.4 租户切换（POST /api/v1/auth/tenant/switch）

`AuthServiceImpl.switchTenant`（`AuthServiceImpl.java:163-175`）：从当前 JWT 主体取 subjectKey → 校验目标租户成员关系（不存在抛 403）→ **重签含新 tenantId 的 access + 新家族 refresh**，旧 access 依赖短 TTL 自然失效。**实现完整**（原设计文档“switchTenant 是 TODO”已过时）。

## 6. JWT 令牌细节

实现：`service/src/main/java/com/ragkb/service/modules/identity/service/impl/TokenServiceImpl.java`（**全部真实实现**）。

| 项 | access | refresh |
| --- | --- | --- |
| 算法 | HS256（`:153`） | 同左 |
| 密钥 | `RAGKB_JWT_SECRET`，启动强制校验 ≥32 字节，否则拒绝启动（`:58-61`） | 同左 |
| issuer | `RAGKB_JWT_ISSUER`，默认 `ragkb`（`:63-64`） | 同左 |
| audience | 固定 `ragkb:web`（`:42`） | 同左 |
| subject | subjectKey（`form|username` 或 `issuer|subject`） | 同左 |
| claims | `jti`、`typ=access`、`uid`、`ten`、`scp`、`rls`（`:139-164`） | `jti`、`typ=refresh`、`uid`、`ten`、`rfid`(refreshFamilyId)，**无角色**（`:93-94`、`:160-162`） |
| TTL | `RAGKB_JWT_ACCESS_TTL`，默认 15m（`:65`） | `RAGKB_JWT_REFRESH_TTL`，默认 30d（`:66`） |
| 严格校验 | `requireIssuer` + `requireAudience` + `require(typ)` + 签名/exp/nbf（`:167-179`） | 同左 |

## 7. Redis 状态存储

| 用途 | Key | 实现 |
| --- | --- | --- |
| refresh 族轮换 + 复用检测 | `auth:rf:{familyId}` = 当前 jti，TTL=refresh 有效期 | Lua CAS：`current==presentedJti` → `SETEX` 新 jti；否则视为旧 refresh 复用 → `DEL` 整族（`RedisRefreshTokenStoreAdapter.java:24-32`） |
| access 黑名单 | `auth:blk:{jti}` = "1"，TTL=access 剩余有效期 | 非正 TTL 不写入（`RedisTokenBlacklistAdapter.java:27-32`） |

**故障策略**：Redis 不可用时刷新/登出直接抛异常，不静默降级放行（`RedisRefreshTokenStoreAdapter.java:18` 注释），与设计文档“Redis 故障默认拒绝”一致。

## 8. 认证过滤器链（form 模式）

挂载顺序见 `SecurityConfig.formFilterChain`（`SecurityConfig.java:87-113`）：

```
ApiKeyAuthenticationFilter (仅 db.enabled=true)
  → JwtAuthenticationFilter
    → CredentialPolicyGateFilter (仅 form+db)
      → authorizeHttpRequests (permitAll 白名单 / 其余 authenticated)
```

| 过滤器 | 职责 | 要点 |
| --- | --- | --- |
| `JwtAuthenticationFilter`（`config/JwtAuthenticationFilter.java:42-69`） | 解析 Bearer JWT + 黑名单 | 无 token 放行（由 URL 规则兜底 401）；authorities = `PermissionCatalog.permissionsForRoles(tenantRoles)` 展开（`JwtAuthenticationFilter.java:59-62`），支撑 `@PreAuthorize("hasAuthority(...)")`；失败返回 401 E-1001 |
| `ApiKeyAuthenticationFilter`（`config/ApiKeyAuthenticationFilter.java:35-99`） | `rk_` 前缀分流 | 命中即按 prefix+digest 查库校验（`ApiKeyDbStore.findActiveByPrefixAndDigest`）；构造 `ApiKeyPrincipal{keyId,tenantId,scopes,allowedKbIds}`，authorities=scopes；`last_used_at` 更新限频 ≥60s |
| `CredentialPolicyGateFilter`（`config/CredentialPolicyGateFilter.java:36-92`） | 每请求重读凭据做策略门禁 | `mustChangePassword`→403 E-1008；`passwordExpiresAt<now`→403 E-1007；白名单 `/change-password`、`/logout`、`/ping`、`/session*`、`/actuator/*`；JWT 主体才检查，匿名/API Key 放行 |

未认证统一 JSON 401 `E-1001`（`restAuthenticationEntryPoint`，`SecurityConfig.java:75-81`）。**CSRF 当前两种模式均禁用**（`SecurityConfig.java:94,170`）。

## 9. 凭据策略（失败锁定/密码过期）—— 骨架在，写路径未接线

| 环节 | 状态 | 说明 |
| --- | --- | --- |
| 失败计数/锁定（`recordLoginFailure`） | ✅ 已实现 | 原子 `failed_attempts+1`，达阈值置 `LOCKED`+`locked_until`（`JdbcUserCredentialStore.java:69-83`） |
| 成功重置（`recordLoginSuccess`） | ✅ 已实现 | 清计数/解锁（`JdbcUserCredentialStore.java:85-94`） |
| 锁定门禁（`isLocked`） | ✅ 已实现 | `LOCKED` 且 `locked_until>now` → disabled；超时自动放行（`JdbcUserDetailsService.java:61-65`） |
| **事件接线**（`CredentialPolicyEventListener`） | ❌ **空壳** | `onAuthenticationFailure/Success` 方法体为空，从未调用 `recordLoginFailure/Success`。**因此失败锁定当前不生效**，`E-1006 ACCOUNT_LOCKED` 已定义但无人抛出 |
| 改密动作 | ❌ 占位 | `JdbcUserCredentialStore.updatePassword` 抛 `UnsupportedOperationException`（`JdbcUserCredentialStore.java:64`）；`AuthService.changePassword` → `TodoSupport`（`AuthServiceImpl.java:320-334`）→ 501。门禁能拦（403），但改密动作返回 501 |

> `CredentialPolicyEventListener`（写路径）与 `CredentialPolicyGateFilter`（读路径）彼此无直接引用：当前只有读路径生效（`adapter/CredentialPolicyEventListener.java:44-52`）。

## 10. 授权模型

### 10.1 权限目录 PermissionCatalog（已接线）

`service/src/main/java/com/ragkb/service/modules/access/service/PermissionCatalog.java`：
- **19 个租户级权限码**（`:27-45`）：`dashboard:view`、`chat:use`、`search:execute`、`kb:list`、`kb:manage`、`document:list`、`favorite:list`、`review:list`、`review:decide`、`metadata-schema:manage`、`retention:manage`、`deletion:read`、`analytics:read`、`analytics:screen`、`tenant-member:manage`、`tag:manage`、`api-key:manage`、`webhook:manage`、`audit:read`。
- **5 个租户角色 → 权限映射**（`TENANT_ROLE_PERMISSIONS`，`:72-82`）+ 基础消费权限（`:62-63`）。
- **3 个 KB 角色 → 内容能力**（`KB_ROLE_PERMISSIONS`，`:85-88`）。
- 未知角色/权限一律不返回（默认拒绝，`:96-111`）。
- 被谁使用：`JwtAuthenticationFilter` 展开 authorities（支撑 `@PreAuthorize`）、`AuthServiceImpl.buildSession` 生成会话权限/features 视图（`AuthServiceImpl.java:278-280`）。

### 10.2 文档三档权限

`service/src/main/java/com/ragkb/service/modules/access/domain/DocumentPermission.java:9-41`：`VIEW_EXCERPT(1) / VIEW_CONTENT(2) / DOWNLOAD_ORIGINAL(3)`，`implies()` 统一展开蕴含（调用方不得各自推断）。

### 10.3 资源级授权 AccessPolicyUseCase —— **已实现但无调用方**

- **db 启用时**：`AccessPolicyServiceImpl`（`service/src/main/java/com/ragkb/service/modules/access/service/AccessPolicyServiceImpl.java:19-133`）判定顺序完整：文档存在 → del_flag → disabled → `lifecycle_status=PUBLISHED` → KB 角色基础能力（`basePermission`，OWNER/EDITOR→可下载，VIEWER→看正文，非成员→拒绝）→ `document_acl` 提升（USER/ORG/TENANT_ROLE/KB_ROLE 取最高档位，`maxAclPermission`/`matchesPrincipal`）→ `implies` 校验 → `allow/deny + reasonCode + policyVersion`。默认拒绝。
- **db 关闭时**：`DenyByDefaultAccessPolicy` 一律 deny（`DenyByDefaultAccessPolicy.java:18-41`）。
- 依赖读取：`KbAccessQueryService.roleOf`（查 `kb_member`）、`DocumentAccessQueryService.viewOf`（查 `document`+`document_acl`）均已实现。
- **但全库无任何调用方**：`decideDocument/canViewExcerpt/canViewContent/canDownloadOriginal` 只在 access 模块内部出现，未接入搜索/预览/下载/引用任何端点；`SubjectContext` 也**无人构造**（认证层主体是 `JwtPrincipal`/`ApiKeyPrincipal`，未转成 `SubjectContext`）。

> ⚠️ **状态枚举不一致（潜伏缺陷）**：`AccessPolicyServiceImpl` 用 `document.lifecycle_status == "PUBLISHED"` 判发布状态（`:23,52`），但 `init.sql:537` 中 `lifecycle_status` 的 CHECK 枚举是 `ACTIVE/ARCHIVED/DELETING/DELETED`（不含 `PUBLISHED`；`PUBLISHED` 属于 `review_status`）。一旦接入调用方，所有文档都会被 `DOCUMENT_STATUS_*` 拒绝。接入前必须先对齐。

### 10.4 方法级授权 @PreAuthorize（已用）

| 位置 | 权限码 |
| --- | --- |
| `UserAccountController`（类级，`UserAccountController.java:35`） | `tenant-member:manage` |
| `ApiKeyController`（类级，`ApiKeyController.java:38`） | `api-key:manage` |
| `AdminController`（`AdminController.java:50,57,65,71`） | `tenant-member:manage` |

## 11. 账号管理（租户成员管理）

`UserAccountController`（`service/src/main/java/com/ragkb/service/modules/identity/controller/UserAccountController.java:34-91`，类级 `@PreAuthorize("tenant-member:manage")`，仅 db.enabled=true 激活）→ `UserAccountServiceImpl`（`service/src/main/java/com/ragkb/service/modules/identity/service/impl/UserAccountServiceImpl.java:46-245`）。

| 能力 | 状态 | 位置 |
| --- | --- | --- |
| `listUsers` 分页 | ✅ 已实现 | `UserAccountServiceImpl.java:85-93`（`selectMemberPage`，tenantId 从 JWT 取） |
| `disableUser` / `enableUser` | ✅ 已实现 | `:96-123`（翻 `tenant_member.status=SUSPENDED/ACTIVE`） |
| `createLocalUser` | ❌ 占位 | `:140-150`（契约注释：单事务插 4 张表） |
| `setRoles` | ❌ 占位 | `:153-158`（覆盖式替换 + 守卫） |
| `removeFromTenant` | ❌ 占位 | `:161-168`（硬删 `tenant_member`，级联清角色/组织） |
| `resetPassword` | ❌ 占位 | `:171-180` |
| `updateUserOrg` | ❌ 占位 | `:127-135`（clear-and-set 单 org） |

> 注释标注的守卫缺口（人工实现时需补）：**不得停用/移出自己、不得移除当前租户最后一名 `TENANT_ADMIN`**（`:97,111,156,163`）。

## 12. API Key（机器访问）

- **明文**：`rk_` + 32 字节 CSPRNG 的 base64url（≥256bit，`ApiKeyCrypto.generateSecret`，`adapter/ApiKeyCrypto.java:43-47`），创建/轮换时只展示一次（`AuthServiceImpl.createApiKey/rotateApiKey`，`AuthServiceImpl.java:187-220`）。
- **存储**：只存 `digest = SHA-256(pepper + raw)`（hex）与 `prefix`（前 10 字符），明文与摘要不进日志（`ApiKeyCrypto.java:50-63`）；pepper 来自 `RAGKB_API_KEY_PEPPER`。
- **校验**：`ApiKeyAuthenticationFilter` 按 prefix+digest 查 `api_key`（`ApiKeyDbStore.findActiveByPrefixAndDigest`，过期视为不存在），构造 `ApiKeyPrincipal`。
- **scope / allowedKbIds**：scope 直接作为 authorities，`allowedKbIds` 存入 `ApiKeyPrincipal` 但当前**没有消费方**（KB 范围未在过滤/策略中强制）。
- **限流**：`rate_limit_per_minute`（默认 60）落库，但**限流执行未实现**（仅 `last_used_at` 写入限频 ≥60s）。
- **吊销/轮换**：逻辑删除 `status=REVOKED` + `revoked_at`（`ApiKeyDbStore.java:87-93`）；轮换更新 digest/prefix（`:97-103`）。
- 幂等：进程内 map（`AuthServiceImpl.guardIdempotency`，`:232-240`），全量落库 `idempotency_record` 为人工实现点（`:66-67`）。

## 13. 前端鉴权与权限门控（web，Next.js 15 App Router）

技术栈：Next.js 15（App Router）+ React 19 + Ant Design 5 + axios；**无 Redux/Zustand**，登录态用 React Context（`web/components/auth-provider.tsx:23-71`）。

| 项 | 现状 |
| --- | --- |
| access token 存储 | **仅内存**（模块级变量），不落 localStorage/sessionStorage（`web/lib/auth.ts:11-24`） |
| refresh token | 前端不读写，后端写 HttpOnly cookie `ragkb_refresh`；axios `withCredentials:true`（`web/api-client/http/client.ts:50`） |
| 登录 | `POST /auth/login` → `setAuth(accessToken, expiresIn)` → `mapCurrentUser(session)` → 跳转（`web/api-client/http/auth.ts:76-84`、`web/app/login/page.tsx:41-42`） |
| 并发 401 单飞刷新 | **模块级 promise 缓存**（非队列）：`refreshPromise ??= performRefresh().finally(...)`（`web/api-client/http/client.ts:80-106`）；刷新失败 `clearSession()` 跳登录 |
| 路由守卫 | `AuthGate`（未登录跳 `/login?from=...`，`mustChangePassword` 强跳改密）+ `RouteGuard`（按 `user.permissions` 判 `requiredAny`，403 页）（`web/components/auth-gate.tsx`、`route-guard.tsx`） |
| 菜单过滤 | `BASE_NAV` 声明 `requiredAny/requiredAll/feature`，`isVisible()` 按 permissions+features 递归过滤（`web/components/nav-config.tsx:77-157`） |
| 按钮级权限 | `PermissionGate` 组件**已定义但未接线**；KB 页实际用 `data.role`（`OWNER/EDITOR/VIEWER`）原始字符串判断（`web/app/(main)/kbs/[id]/page.tsx:98,118,222`） |
| 权限码来源 | HTTP 模式来自后端 `session.permissions`（服务端聚合）；mock 模式硬编码全量权限（`web/api-client/http/auth.ts:51`、`web/mocks/data/users.ts:15-35`） |
| 登出 | `POST /auth/logout` + `finally clearSession()`（`web/api-client/http/auth.ts:98-104`） |
| mock 开关 | `NEXT_PUBLIC_USE_MOCK`（未设置/`true` → mock，任意非空账号密码登录写 `mock-token`；`false` → 接真实后端）（`web/api-client/client.ts:14-17`） |

> 前端权限模型分层清晰：租户/平台能力走 `user.permissions`（服务端聚合），KB 内走 `data.role`，两者未混用；`credentialScopes` 仅作展示字段，**未**把 scope 当 roles 用（原设计文档“P1-6 scope 混淆 roles”已不存在）。

## 14. 审计

- `audit_log` 表/实体/Mapper 存在（`init.sql:1112-1137`；`admin/persistence/entity/AuditLog.java`；`AuditLogMapper.xml`）。
- **无任何写入**：认证/拒绝/账号操作事件均未落库；`AdminServiceImpl.listAuditLogs` 为占位。
- `AuditMetaObjectHandler` 自动填充审计列 `create_by/create_time/update_by/update_time`（取值 `SecurityUtils.currentUserId()`），所有实体继承 `BaseAuditEntity`；`del_flag` 逻辑删除由 MyBatis-Plus 全局生效（`application.yml:30-32`）。

## 15. 多租户

- `sys_user`/`user_credential` 无 tenant 列，全局身份；租户关系只在 `tenant_member`/`tenant_member_role`。
- 登录后激活租户 = 第一个 ACTIVE 成员；刷新/切租户时按 refresh/入参 tenantId **重新从 DB 校验成员关系**（`AuthServiceImpl.java:119-175`），不信任客户端自报。
- `switchTenant` 完整实现（重签新 tenant 的令牌对），见 §5.4。

## 16. 错误码（认证相关）

定义于 `service/src/main/java/com/ragkb/service/common/exception/ErrorCode.java`：

| code | HTTP | 含义 | 当前抛出点 |
| --- | --- | --- | --- |
| `E-1000` | 400 | 参数错误 | 校验 |
| `E-1001` | 401 | 未认证或登录已过期 | EntryPoint、两个认证过滤器、登录失败（`GlobalExceptionHandler.java:43-48`） |
| `E-1002` | 403 | 无权限/成员关系变更 | 刷新成员变更、切租户无权限（`AuthServiceImpl.java:138,167,355`） |
| `E-1003` | 404 | 不存在 | 资源 |
| `E-1004` | 409 | 冲突 | 幂等重复（`AuthServiceImpl.java:238`） |
| `E-1005` | 429 | 限流 | 未使用 |
| `E-1006` | 423 | 账号锁定 | **定义但未抛出**（失败锁定未接线） |
| `E-1007` | 403 | 密码过期 | `CredentialPolicyGateFilter.java:80` |
| `E-1008` | 403 | 首登必须改密 | `CredentialPolicyGateFilter.java:76` |
| `E-9998` | 501 | 未实现 | `TodoSupport`（占位方法） |
| `E-9999` | 500 | 系统错误 | 兜底 |

## 17. 关键配置项

后端环境变量（`service/src/main/resources/application.yml`）：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `RAGKB_AUTH_MODE` | `form` | `form` | `oidc` |
| `RAGKB_DB_ENABLED` | `false` | 身份源落库开关 |
| `RAGKB_DEV_USERNAME/PASSWORD/ROLES` | `admin/admin123/TENANT_ADMIN` | 无库 dev 兜底账号 |
| `RAGKB_OIDC_CLIENT_ID/SECRET/ISSUER_URI` | 空 | oidc 模式必填 |
| `RAGKB_LOCAL_MAX_FAILED_ATTEMPTS` | `5` | 失败锁定阈值（未接线） |
| `RAGKB_LOCAL_LOCKOUT_MINUTES` | `15` | 锁定时长 |
| `RAGKB_LOCAL_PASSWORD_EXPIRY_DAYS` | `180` | 密码过期天数 |
| `RAGKB_JWT_SECRET` | dev 默认 | **生产必填**，≥32 字节，否则拒绝启动 |
| `RAGKB_JWT_ISSUER` | `ragkb` | |
| `RAGKB_JWT_ACCESS_TTL` / `REFRESH_TTL` | `15m` / `30d` | |
| `RAGKB_JWT_REFRESH_COOKIE_MAX_AGE_SECONDS` | `2592000` | |
| `RAGKB_API_KEY_PEPPER` | dev 默认 | 变更会令所有已存 key 失效 |
| `RAGKB_COOKIE_SECURE` | `false` | 生产 https 置 true |
| `RAGKB_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | |
| `REDIS_HOST/PORT/PASSWORD` | `localhost/6379/空` | 由 `deploy/compose` 提供 |

前端环境变量：`NEXT_PUBLIC_USE_MOCK`（默认 true → mock）、`NEXT_PUBLIC_API_BASE_URL`（默认 `http://localhost:8080`，自动追加 `/api/v1`）。

## 18. 已实现 vs 未实现（速查）

### ✅ 已实现且接线
JWT 签发/校验/轮换/黑名单、登录/刷新/登出/会话/切租户、身份目录（内存+DB 双实现）、`@PreAuthorize` 方法级授权、PermissionCatalog、凭据策略**门禁**（首登/过期改密 403）、API Key 全链路、账号查询/停用/启用、前端登录态+单飞刷新+AuthGate/RouteGuard/菜单过滤、bootstrap 管理员 seed。

### ❌ 占位 / 未接线
自助改密、创建本地用户、分配角色、移出租户、重置密码、更新组织、凭据失败锁定**记账**、文档级资源授权（AccessPolicyUseCase 无调用方）、审计落库、IdP 管理、API Key 限流执行、OIDC JIT 建号/CSRF/Session 生产化。

## 19. 遗留风险与差异（与原设计文档对照）

1. **AccessPolicyUseCase 实现与 DDL 枚举不一致**：`lifecycle_status=PUBLISHED` vs CHECK `ACTIVE/ARCHIVED/DELETING/DELETED`（§10.3）——接入前必须先对齐，否则所有文档被拒绝。
2. **授权是"死角"**：`AccessPolicyServiceImpl` 与 `SubjectContext` 均已实现但无任何调用方、无人构造——搜索/预览/下载/引用的越权面仍开放（原设计文档 P0-2 仍成立）。
3. **失败锁定未生效**：`CredentialPolicyEventListener` 空壳，`E-1006` 未抛出（原设计文档 P0-1 部分仍成立）。
4. **占位方法返回 501**：建号/改密等占位接口已暴露到 Controller，调用会得到 `E-9998`；前端目前用 mock 规避。
5. **过期注释**：`SecurityConfig.java:42` 与 `TokenBlacklistPort.java:9`/`RefreshTokenStorePort.java:9` 仍写“JWT/Redis 为人工实现点”，与现状不符，应更新。
6. **OIDC 未生产化**：CSRF 禁用、`JSESSIONID` 默认名（设计要求 `ragkb_session`）、单实例 Session、自定义 callback 与框架 callback 并存、无 JIT 建号——离生产目标仍有差距。
7. **迁移顺序约束**：V0.5 之后不可重跑 V0.4（§4）。
8. **CSRF 全局禁用**：form 模式 cookie（SameSite=Lax）面较小，但 oidc Cookie 模式禁用 CSRF 不可接受（原设计 P0-4 仍成立）。

## 20. 影响说明

- 本文为只读梳理，未修改任何代码、数据库、配置或契约。
- 若要推进到生产授权闭环，优先项：① 对齐并接入 `AccessPolicyUseCase`（含状态枚举修正）；② 接通失败锁定记账（`CredentialPolicyEventListener`）；③ 落地审计写；④ 完成自助改密/建号等占位写操作；⑤ 按原设计文档 §8 的实施顺序逐项推进。
