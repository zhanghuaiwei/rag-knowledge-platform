# Java 后端包结构规范

> **文档状态**：已实施 · **版本**：v0.2.0 · **最近更新**：2026-08-11
> **适用范围**：`service/src/main/java/com/ragkb/service`
> **架构形态**：模块化单体（Modular Monolith），不拆 Maven 子模块，不引入 Spring Cloud

## 1. 结论

后端采用“**业务功能优先，模块内部再分层**”的包结构。它适合当前单体服务，也为后续业务增长保留清晰边界：一次功能修改通常只进入一个 `modules/<feature>`，公共代码和工具代码有明确准入规则，外部系统通过 Port/Adapter 隔离。

本结构是企业级工程的**可演进骨架**，不等于业务已经生产就绪。当前大量 `ServiceImpl` 仍是带方法名的 TODO 占位；事务、权限、持久化、可观测性和业务测试要在各模块实现时继续补齐。

## 2. 目标结构

```text
com.ragkb.service
├── RagkbServiceApplication.java
├── common/                         # 跨两个及以上模块且语义稳定的公共能力
│   ├── api/                        # 统一响应与分页契约
│   ├── exception/                  # 错误码、业务异常、全局异常映射
│   ├── model/                      # TenantId、UserId、Task 等共享值对象
│   ├── event/                      # 跨模块事件发布端口
│   └── storage/                    # 跨模块对象存储端口
├── config/                         # Spring、Security、Database 全局配置
├── health/
│   └── controller/                 # 健康探针，不承载业务逻辑
├── modules/                        # 业务模块总入口
│   ├── identity/
│   ├── knowledge/
│   ├── document/
│   ├── connector/
│   ├── conversation/
│   ├── governance/
│   ├── analytics/
│   ├── admin/
│   ├── task/
│   ├── access/
│   ├── indexing/
│   ├── ingestion/
│   ├── integration/
│   ├── rag/
│   └── tenant/
└── util/                           # 无业务语义、无模块依赖的纯工具
```

功能完整的模块按需使用以下内部层次；没有代码时不创建空目录或占位文件：

```text
modules/<feature>/
├── controller/                     # HTTP 入站适配，只做协议转换/校验/调用
├── dto/                            # 前端请求入参（统一 `*Dto` 后缀，含校验注解）
├── vo/                             # 后端返回对象（统一 `*Vo` 后缀）
├── domain/                         # 聚合、值对象、领域规则；无框架依赖
├── service/                        # 业务用例接口
│   └── impl/                       # 用例编排与事务边界
├── port/                           # 模块需要的外部能力抽象
├── adapter/                        # Port 的外部系统实现
└── persistence/                    # 数据访问实现
    ├── entity/                     # 持久化实体，不直接作为 HTTP 响应
    └── mapper/                     # MyBatis Mapper
```

职责边界：
- `dto/` 只放**前端传递的请求入参**（统一 `*Dto` 后缀，如 `KbCreateDto`），带 Bean Validation 注解，用于反序列化与参数校验。
- `vo/` 只放**后端返回的数据对象**（统一 `*Vo` 后缀，如 `KbVo`），是 Controller/Service 对外返回的形状；禁止把持久化实体直接作为响应（见规则 4）。
- `persistence/entity/` 只放 MyBatis-Plus 实体，**与数据表一一对应**（`@TableName`），业务实现时再在对应 Mapper 的 XML 中写自定义 SQL（`resources/mapper/*.xml`）。
- `persistence/entity/` 只放 MyBatis-Plus 实体，业务实现时再在对应 Mapper 的 XML 中写自定义 SQL（`resources/mapper/*.xml`）。

## 3. 依赖方向

```text
controller -> dto + vo + service
service/impl -> service + dto + vo + domain + port + persistence
adapter -> port
persistence/mapper -> persistence/entity
modules/* -> common
common / util -X-> modules/*
controller -X-> service/impl / persistence
```

规则说明：

1. Controller 保持薄层，不构造 MyBatis 查询、不写业务规则、不直接调用 Mapper。
2. Service 接口表示用例边界；实现类放在 `service/impl`，由 Spring 按接口注入。
3. 外部模型、RAG 引擎、身份源、连接器等依赖先定义 Port，再由 Adapter 实现；业务代码不直接绑定具体 SDK。
4. 持久化实体与 API DTO 分离，禁止把数据库实体作为公共接口契约。
5. 模块间优先通过对方的 Service/Port 或事件协作，禁止跨模块直接访问 Mapper 或写表。
6. 只有多个模块真正复用且语义稳定的类型才能进入 `common`；“以后可能用”不是公共化理由。

## 4. `common` 与 `util` 的边界

| 位置 | 可以放 | 不可以放 |
| --- | --- | --- |
| `common/api` | 统一响应、分页等服务级 API 契约 | 某个业务模块的 Request/Response |
| `common/exception` | 全局错误码、异常与异常映射 | 某模块内部状态机规则 |
| `common/model` | 跨模块值对象、共享任务描述 | 数据库实体、万能 DTO |
| `common/event` / `storage` | 多模块共享的能力端口 | 某个供应商 SDK 实现 |
| `util` | 无状态、无业务语义、无模块依赖的纯函数/辅助类 | Spring Bean、业务规则、数据库或远程调用 |

当一个类无法明确归入上表时，默认留在产生它的业务模块，不要放进 `common` 或 `util`。

## 5. TODO 与空包规则

- 不使用 `package-info.java` 表示“未来会有代码”的空包。
- 尚未实现但已确定的业务用例，应提供真实接口和方法签名，例如 `AccessPolicyUseCase#canViewContent`。
- 已接入 Controller 但业务尚未实现的方法，统一调用 `TodoSupport.notImplemented("Type#method")`；方法名必须可搜索，HTTP 层统一映射为 501 `E-9998`。
- 只有说明、没有方法或契约的候选能力写入任务文档，不进入源码。

## 6. 新增功能的落包流程

1. 确认需求和唯一 OpenAPI 契约，不在代码中另造字段、枚举或错误码。
2. 选择现有 `modules/<feature>`；只有职责和生命周期确实独立时才新增功能包。
3. 先增加 `dto` 和 `service` 用例边界，再实现 `controller` 与 `service/impl`。
4. 需要数据库时，在本模块增加 `persistence/entity|mapper`；需要外部系统时增加 `port|adapter`。
5. 只有出现至少两个稳定消费者后，才评审是否提取到 `common`。
6. 运行 `mvn -B test`；`PackageStructureTest` 会校验目录、包名、依赖方向、TODO 命名和 `package-info.java` 禁令。

## 7. 参考工程的取舍

本次对照了 `kxkj-backend` 的组织方式，吸收了它在业务模块内使用 `controller/dto/entity/mapper/service/impl/vo` 分层的经验，但没有直接复制其工程形态。

| 参考做法 | 本项目取舍 | 原因 |
| --- | --- | --- |
| `modules/<business>` 下按技术职责细分 | 采用 | 功能内聚，文件增长后仍易定位 |
| 公共配置与业务模块分开 | 采用 | 防止配置和业务规则互相污染 |
| 大型 Spring Cloud 多服务、多 Maven 模块 | 不采用 | 当前团队、部署和容量不需要分布式复杂度 |
| Controller 直接构造查询、串联多个 Mapper/Service | 不采用 | 容易形成胖 Controller、N+1 和难测试事务 |
| Entity 贯穿 Controller/Service | 不采用 | 会把数据库模型泄漏成 API 契约 |
| 大量通用工具类集中到 `common` | 不采用 | 容易形成无边界的公共垃圾场 |

## 8. 自动化保护

- `PackageStructureTest`：校验 package 与目录一致、业务代码只能位于 `modules/<feature>/<layer>`、公共/工具包不反向依赖业务模块、Controller 不依赖实现和持久化、TODO 含方法名、源码中不存在 `package-info.java`。
- `RagkbServiceApplicationTest`：验证 Spring 上下文在默认关闭数据库的脚手架模式下仍能完成组件扫描和装配。
- `DatabaseConfig`：Mapper 扫描限定在 `modules` 且只注册带 `@Mapper` 的接口，避免把 Service/Port 接口误注册成 Mapper。
