# 通用企业知识库目标设计

> **文档状态**：已纳入根设计基线 · **版本**：v0.2.0-design · **负责人**：待指定 · **最近更新**：2026-08-10
> **需求**：[`../requirements/README.md`](../requirements/README.md) · **v0.2 架构**：[`../../../02-概要设计.md`](../../../02-概要设计.md) · **v0.1 待同步契约**：[`../../../07-API契约.md`](../../../07-API契约.md)
> **子文档**：[Web 端产品化设计](web-product-design.md)（信息架构、页面与交互） · [Java 后端包结构规范](backend-package-structure.md)（模块化单体与依赖方向） · [认证与授权技术方案](authentication-authorization.md)（认证模式、授权模型、流程与差距） · [Web 动态菜单设计](dynamic-menu.md)（权限驱动菜单、路由和租户切换）

## 1. 设计目标

通过“端口 + 策略 + 事件”实现跨行业通用性：领域用例不依赖具体模型、向量库、对象存储、OCR、身份源或内容源；所有检索和内容访问都使用同一授权决策；来源、版本、策略和索引状态全程可追溯。

## 2. 目标分层

```text
Web / API / Connector Webhook / Admin
                |
        API Gateway / BFF
                |
 Authentication -> Policy Enforcement -> Audit
                |
 Knowledge Domain Services
   |        |          |          |
Source   Governance  Retrieval  Conversation
Sync     & Lifecycle  & Index    & Citation
   |        |          |          |
---------------- Ports ----------------------
Identity | Policy | Parser | OCR | Embedding | Vector/Keyword Index
LLM | Reranker | Object Store | Event Bus | KMS | Malware/DLP
---------------- Adapters -------------------
OIDC/SCIM | SharePoint/Confluence/... | Milvus/... | MinIO/... | Model Providers
```

通用性由稳定端口和能力声明实现，不由大量 `if provider == ...` 分支实现。适配器必须声明支持的格式、区域、数据使用、限流、重试和一致性能力。

## 3. 两条安全闭环

### 3.1 摄取闭环

1. 连接器或上传创建 `sourceObject`，记录租户、来源、外部 ID、版本/ETag、ACL 快照和内容指纹。
2. 原始对象进入 quarantine bucket；执行文件类型、大小/层级、恶意软件、DLP/密钥和策略校验。
3. 通过后解析正文/结构/图片/OCR，生成不可变文档版本与 provenance；失败记录可定位阶段并可重放。
4. 元数据 schema、分类、所有者和审核策略校验；未满足发布条件的版本不可进入在线索引别名。
5. 根据冻结的 `indexProfileVersion` 构建关键词/向量索引，写入租户、文档、版本、发布、禁用、策略版本、来源和时间等过滤字段。
6. 完成数量与质量校验后原子发布索引版本；事件失败进入 outbox/重试队列。
7. 来源更新、撤权、删除、ACL 或审核变化通过同一事件链更新索引；在传播完成前按数据库策略二次过滤。

### 3.2 查询闭环

1. 网关验证 OIDC/API Key/工作负载凭证，形成 `SubjectContext`，不接受客户端自报 tenant 或角色。
2. Policy Decision Point 根据 tenant、subject、scope、KB、文档状态和策略版本计算授权过滤条件。
3. server 仅向 rag-engine 发送已验证的 `RetrievalAccessContext`；它至少包含 tenant、允许 KB、文档过滤或可验证策略句柄、policyVersion 和有效期。
4. rag-engine 验证服务身份和授权上下文，缺失/过期/不一致时拒绝；检索同时过滤当前版本、PUBLISHED、未禁用和授权条件。
5. 对候选结果在 server 或独立策略执行点二次校验，任何不一致候选被剔除并产生安全指标。
6. 模型上下文将检索内容标记为不可信引用数据；系统指令与文档数据分区，执行输入/输出 DLP 和链接/HTML 净化。
7. 答案落库时记录模型、提示模板版本、检索配置、策略版本、文档版本、引用和质量指标；访问历史引用时重新授权。

## 4. 稳定端口

| 端口 | 最小职责 | 关键约束 |
| --- | --- | --- |
| `ContentConnector` | discover/fetch/delta/webhook/acl/tombstone/health | 游标可恢复、幂等、源删除与撤权可传播 |
| `IdentityProvider` | OIDC 登录、用户/组映射、SCIM 生命周期 | 外部身份与租户成员分离 |
| `PolicyDecisionPoint` | authorize/listFilter/explain/policyVersion | 默认拒绝，所有访问面共用 |
| `ParserProvider` / `OcrProvider` | 结构化内容与定位信息 | 沙箱运行，保留页/段/单元格来源 |
| `ContentSafetyProvider` | malware/DLP/secret/injection scan | 扫描失败默认隔离，结论可审计 |
| `EmbeddingProvider` | embed + capability descriptor | 模型/维度/归一化形成不可变 profile |
| `VectorIndex` / `KeywordIndex` | build/query/alias/snapshot/delete | 支持版本化、授权过滤和重建 |
| `RerankerProvider` / `LlmProvider` | rerank/generate/stream | 受数据分类、区域、预算和超时策略控制 |
| `ObjectStore` | quarantine/version/read/delete-proof | 加密、版本、保留/法律保全可表达 |
| `EventPublisher` | outbox/publish/replay/dead-letter | 至少一次投递 + 消费幂等 |

## 5. 待契约评审的接口影响

以下是必须进入现有 OpenAPI 的能力，不是第二份权威契约：

- `QueryChatRequest` 与 `SearchRequest`：加入由 server 生成的授权上下文，rag-engine 不得接收来自浏览器的主体/租户字段；`kbIds`、document filter、policyVersion 均需约束。
- ingestion/delete/task：携带 tenant、kb、document、version、indexProfileVersion 和幂等键；删除必须校验资源归属并返回各索引处置结果。
- 内容访问拆分为 excerpt/preview/download，分别绑定 `view_excerpt`、`view_content`、`download_original`。
- API Key：增加 scope、allowedKbIds、expiresAt、lastUsedAt、revokedAt；创建明文只返回一次。
- 新增连接器、同步任务、元数据 schema、分类/保留策略、索引重建和删除证明资源。
- 所有异步命令使用 `Idempotency-Key`；长任务返回统一 task resource，并定义状态、进度、错误、取消与重试语义。
- 所有列表统一分页/排序/filter；所有错误统一 requestId、稳定 code、字段级 details；所有写接口明确审计动作和权限。

契约评审完成后先更新 `docs/api/*.openapi.yaml`，再生成 server DTO、rag-engine Pydantic 模型和 web client 类型；人工摘要只引用生成结果。

## 6. 数据能力与完整性

v0.2 DDL 已补充 `tenant_member`、`source_connection`、`source_object`、`sync_job`、`metadata_schema`、`document_metadata`、`retention_policy`、`index_profile`、`index_build`、`policy_snapshot`、`outbox_event`、`webhook_delivery`、`deletion_task/target/receipt` 等实体。目录/用户组在基线中统一映射到有 `source + external_id` 的 `sys_org/sys_user_org`；若首批 SCIM IdP 需要与部门不同的嵌套组语义，再通过契约评审拆分独立 group 表。分类基线由 `sensitivity + metadata schema + retention/model route policy` 组合表达，行业自定义分级进入 P1 策略扩展。

数据评审时遵循：

- 所有租户内父子关系同时约束 tenant；关键关联不能只凭全局 id。
- 状态、角色、权限、数值范围和时间顺序由数据库约束兜底。
- 原始内容版本不可变；“当前版本”和“在线索引别名”是显式指针。
- 审计、审核、同步、索引、策略和删除事件追加写，业务状态可由事件追溯。
- 事务数据库通过 outbox 与索引/对象存储最终一致；Seata 不用于伪装无法原子提交的外部索引事务。
- Schema 变更使用可校验、可回滚、可追踪的迁移工具；禁止以手工脚本顺序作为唯一生产状态来源。

## 7. 兼容与迁移

1. 先补契约测试和授权泄漏回归，不改变线上行为。
2. 引入统一 Policy Decision Point；旧 `kb_member/document_acl` 作为首个策略数据源，双算并比较结果。
3. 新 Query/Search 契约启用强制授权上下文；旧内部调用只在短期兼容窗口内允许并记录告警，随后关闭。
4. 为现有文档回填 index profile 与索引元数据，构建新 collection/索引；质量和数量校验后切换别名。
5. 身份迁移到“全局身份 + 租户成员”，保持旧 user id 映射；切换 OIDC 后删除密码 grant。
6. 连接器、元数据和治理按租户灰度；每一步都支持停止消费、回切旧索引别名和重放 outbox。

## 8. 关键失败策略

- 授权服务、策略快照或服务身份验证不可用：受保护内容查询默认失败，不降级为全量检索。
- 索引状态更新失败：数据库保留最新权限真相，查询二次校验；超过传播 SLA 自动告警并暂停受影响库问答。
- 外部模型不可用：只在数据分类允许的 provider 集合内降级，不允许为可用性绕过驻留/敏感策略。
- 连接器限流或源不可用：保留游标、退避重试；超过新鲜度 SLA 将知识库标记 stale，不删除上次成功版本。
- DLP/恶意软件扫描不可用：新内容停留隔离区；已有已发布内容不受影响。
