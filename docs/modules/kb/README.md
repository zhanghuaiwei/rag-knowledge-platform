# 知识库（Knowledge Base）模块

> **文档状态**：业务实现记录 · **版本**：v0.1 · **最近更新**：2026-08-17

本模块覆盖知识库根资源的生命周期（创建 / 更新 / 归档 / 删除 / 克隆）、成员管理与索引构建登记的后端业务实现。

## 文档导航

- [知识库生命周期、成员管理与索引构建实现](design/knowledge-base-lifecycle.md)：各用例的业务规则、数据流转（含跨服务调用 mermaid 图）、边界与遗留风险。

## 权威源与边界

- HTTP 契约以 `docs/api/server.openapi.yaml` 与 `web/api-client/`（前端契约类型）为准；产品补充字段（role/members/documentCount 等）以 `KbVo` 注释为准。
- 表结构与 CHECK 约束以 `deploy/ddl/init.sql` + `deploy/ddl/migrations/` 为准。
- 实现代码：`service/src/main/java/com/ragkb/service/modules/knowledge/`（Controller 不含业务逻辑，用例见 `KbServiceImpl`）。
