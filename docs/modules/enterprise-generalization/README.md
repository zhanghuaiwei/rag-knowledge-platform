# 企业知识库通用化改造

> **文档状态**：已纳入 v0.2 设计基线 · **版本**：v0.2.0-design · **负责人**：待指定 · **最近更新**：2026-08-10

本模块记录现有“企业级 RAG 知识库平台”向通用企业知识库收敛所需的差距、目标架构、实施任务和验收门禁。差距结论已经纳入根目录 `00/02/03/04/05/06` 与 v0.2 DDL；本模块继续作为 GKB 需求、实施任务、测试和运维门禁的权威入口。

## 文档导航

- [需求与差距分析](requirements/README.md)：结论、优先级、通用能力基线和验收标准。
- [目标设计](design/README.md)：授权闭环、摄取闭环、扩展端口和迁移顺序。
- [Web 端产品化设计](design/web-product-design.md)：产品定位、角色、信息架构、页面与交互设计、分期和待确认问题（待评审，不冻结契约）。
- [后端任务](tasks/backend/README.md)：契约、安全、连接器、治理和可靠性任务。
- [前端任务](tasks/frontend/README.md)：统一认证、连接器、治理和可解释问答体验。
- [测试与验收](test/README.md)：安全、契约、数据隔离、同步、RAG 质量和灾备门禁。
- [运维基线](ops/README.md)：SLO、RPO/RTO、备份恢复、安全运营和发布门禁。

## 权威源与边界

- v0.1 功能编号 F2.x 仍以 [`../../01-需求分析.md`](../../01-需求分析.md) 为准；通用化增量 GKB-x 以本模块 [`requirements/README.md`](requirements/README.md) 为准。
- 当前 HTTP/SSE 契约仍以 [`../../api/server.openapi.yaml`](../../api/server.openapi.yaml) 和 [`../../api/rag-engine.openapi.yaml`](../../api/rag-engine.openapi.yaml) 为准。
- 领域模型、表结构与迁移策略以 [`../../04-数据库设计.md`](../../04-数据库设计.md) 和仓库根目录 `deploy/ddl/init.sql`（v0.2 单文件初始化）为准。
- 本模块中的新增 HTTP 接口、实体和字段仍只表示待确认的契约影响；完成 OpenAPI 评审前不得据此实现调用方。
- **实现现状（2026-08）**：`web/` 产品化页面 + mock/http 双 transport 已实现（antd/G6/ECharts，`NEXT_PUBLIC_USE_MOCK` 切换）；`service/` 为完整接口入口骨架（Controller/DTO/错误码已落地，Spring Security form/OIDC + MyBatis-Plus 骨架已接入），业务用例仍为 `NotYetImplemented` stub（501 `E-9998`），待契约冻结后人工实现；`rag-engine/` 为探针骨架。本模块不把 mock、计划或 stub 描述为业务验收通过。
