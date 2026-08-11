# ragkb-service — 领域 API 服务

> 通用企业知识库平台的 **Java 领域 API 服务**,采用 **模块化单体 + 六边形架构**。当前为 v0.2 接口入口骨架:全部 HTTP 接口、DTO 与错误码已落地,并引入 Spring Security(form/OIDC)与 MyBatis-Plus 数据访问骨架;业务用例仍为人工实现点(未实现时返回 501 `E-9998`)。

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

## 架构分层

```text
interfaces   HTTP 适配层:Controller 只做参数校验与转发,不含业务逻辑
   │
application  应用层:use cases、事务边界、授权编排、幂等
   │
   ├── domain(模块化)
   │      identity / access / knowledge / connector / governance /
   │      indexing / ingestion / conversation / integration / analytics
   │
   ├── ports      领域端口(接口):IdentityProviderPort / ContentConnector /
   │              SearchIndex / ModelProvider / ObjectStore / EventTransport ...
   └── adapters   适配器实现:PostgreSQL、Redis、S3、OpenSearch、IdP、模型 SDK
```

各业务模块边界见 `src/main/java/com/ragkb/service/<module>/package-info.java`,模块职责以 `03-详细设计` 为准。**领域层只依赖 `ports`,不依赖供应商 SDK**。

## 目录结构

```text
service/
├── pom.xml
└── src
    ├── main
    │   ├── java/com/ragkb/service/
    │   │   ├── RagkbServiceApplication.java   # 启动入口
    │   │   ├── common/                         # TenantId/UserId/PageRequest/错误码/统一响应
    │   │   ├── interfaces/                     # HTTP 控制器(含 /api/v1/ping 探针)
    │   │   ├── application/                    # 应用层用例
    │   │   ├── ports/                          # 领域端口(接口)
    │   │   ├── adapters/                       # 适配器实现
    │   │   └── <module>/                       # 各业务域模块骨架
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

由环境变量 `RAGKB_AUTH_MODE` 控制(默认 `form`),见 `interfaces/config/SecurityConfig.java`:

- **form**(开发/演示默认):`POST /api/v1/auth/login` 账号密码登录;开发用内存用户(`RAGKB_DEV_USERNAME / RAGKB_DEV_PASSWORD / RAGKB_DEV_ROLES`)。⚠️ 真实用户体系(`sys_user`/`identity_account`)由人工按 `../04-数据库设计` 接入。
- **oidc**(生产):`GET /api/v1/auth/authorize` 重定向企业 IdP,配置 `RAGKB_OIDC_CLIENT_ID / RAGKB_OIDC_CLIENT_SECRET / RAGKB_OIDC_ISSUER_URI`。
- 会话采用 BFF HttpOnly cookie(JSESSIONID),未认证 API 返回 JSON 401(`E-1001`)。
- ⚠️ 脚手架说明:CSRF 暂未启用,生产 cookie 会话必须启用(见类注释)。

## 数据库开关(ragkb.db.enabled)

由环境变量 `RAGKB_DB_ENABLED` 控制(默认 `false`):

- **关闭**(默认):不创建 DataSource,service 无数据库也能启动(脚手架阶段)。
- **开启**:按 `spring.datasource.*` 连接 PostgreSQL(`RAGKB_DB_URL / RAGKB_DB_USERNAME / RAGKB_DB_PASSWORD`),注册 MyBatis-Plus 分页/乐观锁拦截器并扫描 Mapper。Schema 由 `../deploy/ddl/init.sql` 人工执行,启动不建表。
- 骨架模板:`adapters/persistence/entity/SysTenant.java`(`@Version` 乐观锁)+ `adapters/persistence/mapper/SysTenantMapper.java`(`BaseMapper`),供后续表复制。

## 当前实现状态(接口骨架)

- **全部 HTTP 接口已落地**:`interfaces/` 下 11 个 Controller,路径/方法/DTO/错误码对齐 `../docs/api/server.openapi.yaml`(web ↔ server v0.2 草稿)。
- **业务用例为 stub**:`application/impl/*.java` 大部分方法抛出 `NotYetImplemented.stub(...)`,`GlobalExceptionHandler` 映射为 HTTP 501 `E-9998`。已实现端到端:`/api/v1/ping` 探针、认证(登录/会话/登出)与 `AuthServiceImpl.session()`。
- **rag-engine 对接留口子**:`adapters/ragengine/RagEngineHttpClient.java` 实现 `RagEnginePort`,当前为 stub,后续按 `../docs/api/rag-engine.openapi.yaml` 人工实现。

## 配置

- `src/main/resources/application.yml`:应用名、端口、Actuator 探针、认证/数据库/rag-engine 开关。
- 数据库、对象存储、模型等业务数据源在对应模块实现时接入;**连接凭证一律通过环境变量 / Secret Manager,禁止硬编码**。

## 提交规范

- 新功能先确认需求与唯一 API 契约(以 `../docs/api/server.openapi.yaml` 为准),再按模块实现。
- 提交信息遵循仓库 `CONTRIBUTING.md` 的 Conventional Commits;提交前通过 `make lint / test / security`。

## 当前状态与后续

- [x] Spring Boot 工程、Actuator 探针、`/api/v1/ping`
- [x] 模块化包结构(`common / interfaces / application / ports / adapters` + 13 个业务域)
- [x] 全部 HTTP 接口入口骨架(Controller/DTO/错误码,见上"当前实现状态")
- [x] Spring Security 账号密码登录(form)+ OIDC 模式开关(环境变量切换)
- [x] MyBatis-Plus 数据访问骨架(`ragkb.db.enabled` 开关 + SysTenant 模板)
- [ ] 按 v0.2 OpenAPI 冻结契约逐模块实现业务用例(替换 `NotYetImplemented` stub)
- [ ] 接入 Flyway 管理 `../deploy/ddl/init.sql` Schema
- [ ] 真实用户/租户体系接入 DB(`sys_user`/`identity_account`/`tenant_member`)
