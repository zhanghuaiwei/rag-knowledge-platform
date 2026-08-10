# ragkb-service — 领域 API 服务

> 通用企业知识库平台的 **Java 领域 API 服务**,采用 **模块化单体 + 六边形架构** 骨架。当前为 v0.2 架构骨架阶段,仅提供健康检查与最小接口适配验证;业务模块在 OpenAPI v0.2 契约冻结后按模块实现。

- 权威设计:`../docs/03-详细设计.md`、`../docs/05-技术选型.md`
- 数据库:`../deploy/ddl/`

## 技术栈

| 项 | 版本 |
| --- | --- |
| Java | 21 (LTS) |
| Spring Boot | 3.4 |
| Spring Web / Actuator / Validation | 随 Boot 管理 |
| 构建 | Maven 3.9+ |
| 数据库 | PostgreSQL 16+(按模块接入,见 `../deploy/ddl/`) |

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

## 配置

- `src/main/resources/application.yml`:应用名、端口、Actuator 探针。
- 数据库、对象存储、模型等业务数据源在对应模块实现时接入;**连接凭证一律通过环境变量 / Secret Manager,禁止硬编码**。

## 提交规范

- 新功能先确认需求与唯一 API 契约(以 `../docs/api/server.openapi.yaml` 为准),再按模块实现。
- 提交信息遵循仓库 `CONTRIBUTING.md` 的 Conventional Commits;提交前通过 `make lint / test / security`。

## 当前状态与后续

- [x] Spring Boot 工程、Actuator 探针、`/api/v1/ping`
- [x] 模块化包结构(`common / interfaces / application / ports / adapters` + 13 个业务域)
- [ ] 按 v0.2 OpenAPI 冻结契约逐模块实现业务用例
- [ ] 接入 Flyway 管理 `../deploy/ddl/` Schema
- [ ] 接入 Spring Security OAuth2 Resource Server(OIDC)
