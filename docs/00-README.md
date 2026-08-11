# 通用企业知识库平台 — 文档索引

> **文档状态**：评审中 · **版本**：v0.2.0-design · **负责人**：待指定 · **最近更新**：2026-08-10
> **本次变更**：将 v0.1 的“上传型 RAG 平台”收敛为身份、内容源、策略、索引和模型可替换的通用企业知识库设计基线。

## 1. 项目定位

**一句话**：为 SaaS 与私有化场景提供知识接入、治理、检索、引用问答和开放集成能力，并保证租户、权限、来源、版本和模型使用全程可追溯。

平台不是单一聊天机器人，也不把某个云厂商、向量库或模型写入领域语义。其核心闭环为：

```text
企业身份与组织
      ↓
内容源接入 → 安全隔离 → 解析/OCR → 元数据/审核/保留 → 版本化索引
                                                      ↓
             统一授权 → 搜索/问答 → 引用与反馈 → 质量/成本/审计
```

### 1.1 通用能力域

| 能力域 | 目标 |
| --- | --- |
| 身份与租户 | OIDC Code + PKCE、租户成员、组织/用户组、预留 SCIM/SAML |
| 知识接入 | 上传、对象存储、SharePoint/OneDrive、Confluence、Web 等连接器端口 |
| 内容安全 | quarantine、真实类型、压缩炸弹、恶意软件、DLP/密钥、间接提示词注入检测 |
| 知识治理 | 来源血缘、不可变版本、元数据 schema、分类分级、审核、复审、保留与法律保全 |
| 检索与索引 | 关键词 + 向量 + 融合 + 精排；不可变 index profile；别名原子切换 |
| 权限与审计 | 租户/KB/文档 allow-list、统一策略决策、fail-closed、追加写审计 |
| 可信问答 | 授权检索、可定位引用、拒答、版本/策略可追溯、AI 安全与持续评测 |
| 开放运营 | 有 scope 的 API Key、Webhook、配额、SLO、RPO/RTO、成本与质量分析 |

### 1.2 逻辑组成

| 组件 | 责任 | 默认技术边界 |
| --- | --- | --- |
| web | 管理、搜索、问答、治理与质量体验 | Next.js；仅调用生成的 API client；不保存长期 token |
| server | BFF/API、领域用例、策略执行、事务与审计 | Spring Boot；PostgreSQL 为业务真相；outbox 驱动异步一致性 |
| rag-engine | 解析、embedding、检索、精排、生成 | FastAPI/Python；只接受已认证服务与签名授权上下文 |
| workers | 连接器同步、内容安全、索引构建、归档删除、Webhook | 可与 server/engine 同制品起步，按负载独立扩展 |
| adapters | IdP、连接器、对象存储、搜索索引、模型、OCR、事件 | 通过稳定端口替换；领域层不直接依赖供应商 SDK |

## 2. 文档权威源

| 事实类型 | 权威源 | 状态 |
| --- | --- | --- |
| v0.1 功能编号 F2.x | [`01-需求分析.md`](01-需求分析.md) | **功能基线保留，但其中技术选型表述已被 v0.2 ADR 否决**，见文内 ⚠️ 标注 |
| 通用化需求 GKB-x | [`modules/enterprise-generalization/requirements/README.md`](modules/enterprise-generalization/requirements/README.md) | 评审中，本次设计依据 |
| 模块边界与数据流 | [`02-概要设计.md`](02-概要设计.md) | v0.2 评审中 |
| 领域接口、状态机与安全闭环 | [`03-详细设计.md`](03-详细设计.md) | v0.2 评审中 |
| 关系模型、约束和迁移 | [`04-数据库设计.md`](04-数据库设计.md) | v0.2 评审中 |
| 技术决策与替换边界 | [`05-技术选型.md`](05-技术选型.md) | v0.2 评审中 |
| 部署、安全、可靠性架构 | [`06-架构方案.md`](06-架构方案.md) | v0.2 评审中 |
| HTTP/SSE 契约（server） | `api/server.openapi.yaml` | **v0.2 草稿，评审中，未冻结**（89 路径 / 113 操作，覆盖现有 controller 全部路径）；rag-engine 内部契约待补 |
| 测试与验收 | [`modules/enterprise-generalization/test/README.md`](modules/enterprise-generalization/test/README.md) | 计划，全部 not-run |
| 新装 Schema | `deploy/ddl/init.sql` | 单文件一键初始化（角色+库+48表+种子），未执行 |
| v0.1 扩展迁移 | —（并入 `04-数据库设计.md` §9.2） | 非破坏性 Expand 策略描述，无独立脚本 |
| RLS 纵深防御 | `deploy/ddl/init.sql` 附录 A | 可选启用，需应用先设置 tenant context |
| 中间件编排 | `deploy/compose/docker-compose.yml` | PostgreSQL/Redis/MinIO 最小集（2G 实例） |
| 人工 API 摘要 | [`07-API契约.md`](07-API契约.md) | **v0.1 已废弃**；机器契约唯一权威为 `api/*.openapi.yaml`（当前仍是 v0.1，待评审同步） |
| 开发计划 | [`10-里程碑与开发计划.md`](10-里程碑与开发计划.md) | **v0.1 已废弃**；其排期基于已被 v0.2 否决的架构（Seata/Milvus/Ollama/密码模式），v0.2 计划待重排 |

> v0.2 公共 API 尚未冻结。本轮详细设计中的 HTTP 影响仅用于契约评审，不能替代 OpenAPI。

## 3. 阅读路径

| 角色 | 推荐顺序 |
| --- | --- |
| 产品/领域负责人 | 01 → 通用化 requirements → 02 → 03 的业务状态机 |
| 契约负责人 | requirements → 02 边界 → 03 接口影响 → 07/OpenAPI |
| 后端/RAG | 02 → 03 → 04 → 05 → OpenAPI |
| 前端 | requirements → OpenAPI → 02 页面边界 → 通用化 frontend tasks |
| 数据/DBA | 04 → `deploy/ddl/` → 06 数据与恢复 |
| 安全/运维 | 03 授权与内容安全 → 06 → 09 → 通用化 ops/test |

## 4. 统一约定

- **时间**：数据库 `TIMESTAMPTZ`，接口 ISO 8601 UTC；展示层本地化。
- **标识**：数据库内部使用 BIGINT identity；跨服务事件使用稳定事件 ID；外部来源使用 `connection + externalId` 唯一标识。
- **多租户**：调用方不能自报 tenant；tenant 来自已验证身份/服务上下文。数据库使用复合租户外键，RLS 可作为纵深防御。
- **权限**：文档 ACL 采用 allow-list；有文档 ACL 时覆盖 KB 继承，无 ACL 时继承 KB 成员权限；不支持隐式 deny。
- **内容访问**：`view_excerpt`、`view_content`、`download_original` 分离；返回原始字节的接口必须要求下载权限。
- **一致性**：PostgreSQL/对象版本为事实源，搜索索引与缓存是可重建派生物；跨系统写入使用 outbox + 幂等消费者。
- **索引**：embedding、维度、chunk、metric、语言分析器构成不可变 `indexProfileVersion`；换模必须重建并原子切换别名。
- **安全失败**：身份、授权、内容安全或策略版本不可用时 fail-closed；不得为可用性放宽租户/ACL/数据驻留。
- **秘密信息**：数据库只保存 `secret_ref` 或不可逆摘要，不保存连接器、Webhook、模型和 IdP 明文密钥。
- **状态证据**：草稿、计划、mock、候选扫描和未执行脚本都不算实现或验收通过。

## 5. 术语

| 术语 | 含义 |
| --- | --- |
| SubjectContext | 已验证用户/API Key/服务主体及其 tenant、角色、组、scope |
| PDP / PEP | Policy Decision Point / Policy Enforcement Point，统一策略决策与执行点 |
| RetrievalAccessContext | server 签发给 rag-engine 的短期授权上下文，缺失或过期即拒绝检索 |
| ContentConnector | 内容源端口，包含发现、拉取、增量游标、ACL、删除墓碑与健康检查 |
| SourceObject | 外部内容对象的稳定身份、版本、来源 ACL 和同步状态 |
| DocumentVersion | 不可变的文档内容版本；当前版本由 document 指针选择 |
| IndexProfile | embedding/分块/索引/分析器的不可变配置版本 |
| IndexBuild | 按知识库和 profile 构建的新索引，验证后通过 alias 上线 |
| PolicySnapshot | 某主体在策略版本下的短期可检索文档集合/句柄 |
| Provenance | 内容来源、采集时间、外部版本、处理链和修改历史 |
| Legal Hold | 法律保全；覆盖普通保留/删除策略，解除前禁止处置 |

## 6. 当前限制与联动项

1. **实现现状（2026-08）**：`web/` 产品化页面 + mock/http 双 transport 已实现（antd/G6/ECharts，`NEXT_PUBLIC_USE_MOCK` 切换）；`service/` 为完整接口入口骨架（11 个 Controller、DTO、错误码已落地；Spring Security form/OIDC 认证开关 + MyBatis-Plus `ragkb.db.enabled` 骨架已接入），业务用例仍为 `NotYetImplemented` stub（HTTP 501 `E-9998`），待契约冻结后人工实现；`rag-engine/` 为 `/healthz` 探针骨架（`domain`/`ports` 已就绪）。详见 `web/README.md`、`service/README.md`、`rag-engine/README.md`。
2. `api/server.openapi.yaml` 已升级为 v0.2 草稿（OIDC/BFF、授权上下文、连接器、治理、索引构建、删除证明、scoped API Key 均已覆盖，评审中未冻结；当前 89 路径 / 113 操作，已覆盖现有 controller 全部路径）；`api/rag-engine.openapi.yaml` 仍是 v0.1，server→rag-engine 内部契约（RetrievalAccessContext、服务认证、ingest/query/delete/task）待补。
3. `01-需求分析.md`、`07-API契约.md`、`08-测试与质量评估.md`、`09-部署运维指南.md`、`10-里程碑与开发计划.md` 均为 v0.1 内容并已在文内标注"已废弃/待同步"；其中与 v0.2 冲突的技术选型表述以 v0.2 ADR 为准，`10` 的计划待按 v0.2 架构重排。
4. 所有 DDL 都是文件变更，未连接或修改任何真实数据库。

