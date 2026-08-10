# rag-knowledge-platform 文档入口

本目录用于沉淀需求、设计、API 契约、AI 开发任务、测试、发布和问题支持。

## 核心方案

- [通用企业知识库 v0.2 文档索引](00-README.md)
- [企业知识库通用化需求、任务与门禁](modules/enterprise-generalization/README.md)
- [AI 开发工程整体方案](ai-development-engineering-overall-plan.md)
- [AI 全栈协作文档结构方案](ai-fullstack-doc-structure-plan.md)
- [AI 开发角色上下文方案](ai-role-context-plan.md)
- [前后端 AI 协作流程](frontend-backend-ai-collaboration.md)

## 目录

- `ai/`：AI 使用说明。
- `modules/`：按业务模块维护需求、设计、API、任务、测试、运维和支持文档。
- `templates/`：新功能、契约、任务、测试、发布和治理模板。

## 基本原则

1. 业务文档统一进入 `docs/modules/<module>/`。
2. 同一功能只保留一份权威需求和一份权威 API 契约。
3. 前端与后端任务分离，但引用同一契约版本。
4. 已执行证据、计划和受阻项必须明确区分。
5. 模板复制后及时替换占位内容，不把模板值当成真实结论。
