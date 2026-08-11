# ragkb-service — 领域 API 服务

> 通用企业知识库平台的 **Java 领域 API 服务**,采用 **模块化单体 + 业务功能优先 + Port/Adapter 边界**。当前为 v0.2 接口入口骨架:全部 HTTP 接口、DTO 与错误码已落地,并引入 Spring Security(form/OIDC)与 MyBatis-Plus 数据访问骨架;业务用例仍为人工实现点(未实现时返回 501 `E-9998`)。

- 权威设计:`../docs/03-详细设计.md`、`../docs/05-技术选型.md`
- 数据库:`../deploy/ddl/init.sql`（单文件一键初始化，48 张表 + 最小种子）

## 技术栈

| 项 | 版本 |
| --- | --- |
| Java | 21 (LTS) |
| Spring Boot | 3.4 |
| Spring Web / Actuator / Validation | 随 Boot 管理 |
| Spring Security / OAuth2 Client | 随 Boot 管理(认证开关见下文) |
| MyBatis-Plus | 3.5.17(spring-boot3 starter + jsqlparser 分页插件) |
| 构建 | Maven 3.9+ |
| 数据库 | PostgreSQL 16+(`ragkb.db.enabled` 开关,Schema 见 `../deploy/ddl/init.sql`) |

## 包结构

```text
com.ragkb.service
├── common
│   ├── api          统一响应与分页契约
│   ├── exception    错误码、业务异常、全局异常映射
│   ├── model        跨功能值对象
│   ├── event        跨功能事件端口
│   └── storage      跨功能对象存储端口
├── config              Spring / Security / Database 配置
├── health/controller   健康探针
├── modules
│   ├── identity / knowledge / document / connector
│   ├── conversation / governance / analytics / admin / task
│   ├── access / indexing / ingestion / integration / rag / tenant
│   └── <feature>
│       ├── controller / dto / vo / domain
│       ├── service/impl
│       ├── port / adapter
│       └── persistence/entity|mapper
└── util                无业务语义的通用工具
```

采用 **package-by-feature + feature 内部分层**：修改一个功能时，它的 HTTP 入口、数据对象、用例和适配器在同一模块中内聚；模块变大后仍可按职责定位。
`common` 不放业务规则，`util` 不依赖任何功能包；功能包只通过明确的 Service/Port 或事件协作，禁止跨功能直接写表。
未实现的领域使用带明确方法名的 `*UseCase` / `*Port` 作为 TODO 占位，不再使用只含说明的 `package-info.java`。

完整规则见 [`../docs/modules/enterprise-generalization/design/backend-package-structure.md`](../docs/modules/enterprise-generalization/design/backend-package-structure.md)。

## 目录结构

```text
service/
├── pom.xml
└── src
    ├── main
    │   ├── java/com/ragkb/service/
    │   │   ├── RagkbServiceApplication.java   # 启动入口
    │   │   ├── common/                         # 跨功能公共能力
    │   │   ├── config/                         # 全局配置
    │   │   ├── util/                           # 通用工具
    │   │   └── modules/<feature>/              # 业务模块及其内部层次
    │   └── resources/
    │       └── application.yml                 # 配置(凭证一律走环境变量)
    └── test
```

## 快速开始

### 环境要求

- JDK **21**(LTS)。注意:本机若仅有 JDK 8,无法构建,CI(`../.github/workflows/ci.yml`)使用 JDK 21。
- Maven 3.9+。

### 构建与测试

```bash
cd service
mvn -B -DskipTests package   # 构建
mvn -B test                  # 测试
```

### 本地运行

```bash
cd service && mvn spring-boot:run
# 默认端口 8080
```

启动后验证:

```bash
curl http://localhost:8080/api/v1/ping
# {"code":"0","message":"OK","data":{"service":"ragkb-service","status":"ok","phase":"scaffold"}}

curl http://localhost:8080/actuator/health
```

## 认证模式(form / oidc 开关)

由环境变量 `RAGKB_AUTH_MODE` 控制(默认 `form`),见 `config/SecurityConfig.java`:

- **form**(开发/演示默认):`POST /api/v1/auth/login` 账号密码登录 + JWT。开发用内存用户(`RAGKB_DEV_USERNAME / RAGKB_DEV_PASSWORD / RAGKB_DEV_ROLES`)。
  - 登录签发 access token(响应体,前端仅内存持有)+ refresh token(HttpOnly cookie `ragkb_refresh`,SameSite=Lax,Path=/api/v1/auth,生产加 Secure);刷新走 `POST /api/v1/auth/refresh`(轮换 + 复用检测,复用检测失败吊销整族);登出黑名单 access jti + 吊销 refresh 家族。请求经 `JwtAuthenticationFilter` 校验 `Authorization: Bearer`。
  - **JWT 签发/校验与 Redis 黑名单/族存储为人工实现点**:`modules/identity/service/impl/TokenServiceImpl`(JJWT,`RAGKB_JWT_SECRET` 启动校验非空)、`modules/identity/adapter/RedisTokenBlacklistAdapter`(key `auth:blk:{jti}`)、`RedisRefreshTokenStoreAdapter`(key `auth:rf:{familyId}`)。
  - 配置:`RAGKB_JWT_ISSUER / RAGKB_JWT_ACCESS_TTL`(默认 15m)/ `RAGKB_JWT_REFRESH_TTL`(默认 30d);Redis 走 `REDIS_HOST / REDIS_PORT / REDIS_PASSWORD`(deploy/compose 已就位)。
  - ⚠️ 真实用户体系(`sys_user`/`identity_account`)由人工按 `../04-数据库设计` 接入。
- **oidc**(生产):`GET /api/v1/auth/authorize` 重定向企业 IdP,配置 `RAGKB_OIDC_CLIENT_ID / RAGKB_OIDC_CLIENT_SECRET / RAGKB_OIDC_ISSUER_URI`;会话采用 BFF HttpOnly cookie(JSESSIONID),现状未动。
- 未认证 API 统一返回 JSON 401(`E-1001`)。
- ⚠️ 脚手架说明:CSRF 暂未启用。form 模式登录/刷新/登出端点为 permitAll + HttpOnly cookie(SameSite=Lax,跨站 POST 不携带),CSRF 面小但存在;生产如需收紧加 Origin 校验或 double-submit token(见 `SecurityConfig` 类注释)。

## 数据库开关(ragkb.db.enabled)

由环境变量 `RAGKB_DB_ENABLED` 控制(默认 `false`):

- **关闭**(默认):不创建 DataSource,service 无数据库也能启动(脚手架阶段)。
- **开启**:按 `spring.datasource.*` 连接 PostgreSQL(`RAGKB_DB_URL / RAGKB_DB_USERNAME / RAGKB_DB_PASSWORD`),注册 MyBatis-Plus 分页/乐观锁拦截器并扫描 Mapper。Schema 由 `../deploy/ddl/init.sql` 初始化(`deploy/compose` 首次启动自动执行),应用启动不建表。
- 骨架模板:`modules/tenant/persistence/entity/SysTenant.java`(`@Version` 乐观锁)+ `modules/tenant/persistence/mapper/SysTenantMapper.java`(`BaseMapper`),供后续表复制。

## 当前实现状态(接口骨架)

- **全部 HTTP 接口已落地**:各功能包内的 Controller 路径/方法/DTO/错误码对齐 `../docs/api/server.openapi.yaml`(web ↔ server v0.2 草稿)。
- **业务用例为 TODO 占位**:各功能包的 `*ServiceImpl` 大部分方法调用 `TodoSupport.notImplemented(...)`,`GlobalExceptionHandler` 映射为 HTTP 501 `E-9998`。已实现端到端:`/api/v1/ping` 探针与 `AuthServiceImpl.session()`(OIDC/内存用户主体)。
- **认证改为 JWT 骨架**:form 模式的登录/刷新/登出、JWT 签发/校验与 Redis 黑名单均为 TodoSupport 桩(`AuthService#login/refresh/logout`、`TokenService.*`、两个 Redis Adapter),由人工按 `TokenService`/Port 契约实现(见上"认证模式");实现完成前 form 模式受保护请求 500/501。
- **rag-engine 对接留口子**:`modules/rag/adapter/RagEngineHttpClient.java` 实现 `modules/rag/port/RagEnginePort`,当前为 TODO 占位,后续按 `../docs/api/rag-engine.openapi.yaml` 人工实现。

## 配置

- `src/main/resources/application.yml`:应用名、端口、Actuator 探针、认证/数据库/rag-engine 开关。
- 数据库、对象存储、模型等业务数据源在对应模块实现时接入;**连接凭证一律通过环境变量 / Secret Manager,禁止硬编码**。

## 提交规范

- 新功能先确认需求与唯一 API 契约(以 `../docs/api/server.openapi.yaml` 为准),再按模块实现。
- 提交信息遵循仓库 `CONTRIBUTING.md` 的 Conventional Commits;提交前通过 `make lint / test / security`。

## 当前状态与后续

- [x] Spring Boot 工程、Actuator 探针、`/api/v1/ping`
- [x] 按功能组织的模块化包结构（`modules/<feature>/<layer>` + `common/config/util`）
- [x] 包结构自动化约束（目录/包名/依赖方向/TODO 命名/禁止 `package-info.java`）
- [x] 全部 HTTP 接口入口骨架(Controller/DTO/错误码,见上"当前实现状态")
- [x] Spring Security 认证骨架:form 模式账号密码登录 + JWT access/refresh(桩)+ OIDC 模式开关(环境变量切换)
- [x] MyBatis-Plus 数据访问骨架(`ragkb.db.enabled` 开关 + SysTenant 模板)
- [ ] 按 v0.2 OpenAPI 冻结契约逐模块实现业务用例（替换 `TodoSupport.notImplemented` 占位）
- [ ] 接入 Flyway 管理 `../deploy/ddl/init.sql` Schema
- [ ] 真实用户/租户体系接入 DB(`sys_user`/`identity_account`/`tenant_member`)
