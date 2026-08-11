# 企业通用化测试与验收

> **文档状态**：计划 · **版本**：v0.2-draft · **负责人**：待指定 · **最近更新**：2026-08-10
> **需求**：[`../requirements/README.md`](../requirements/README.md) · **设计**：[`../design/README.md`](../design/README.md)

**测试命令现状（2026-08）**：仓库已具备可执行测试命令——`service`：`mvn -B test`（接口骨架编译/单测）；`web`：`pnpm test`（Vitest，含 `app/page`、`api-client/http/sse`、`api-client/mock`、`lib/format` 等用例）+ `pnpm lint / typecheck`；`rag-engine`：`uv run pytest -q`（`/healthz` 冒烟）。但业务用例仍为 `NotYetImplemented` stub，**下表业务验收矩阵仍全部为 `not-run`，不得作为通过证据**；待 service 业务用例实现后逐项执行。

## 1. Gate 0 验收矩阵

| ID | 场景 | 层级 | 期望 | 状态 |
| --- | --- | --- | --- | --- |
| AUTH-01 | 用户可访问 KB 但被文档 ACL 排除，发起问答 | integration/E2E | 答案、引用、日志和历史均不含该文档 | not-run |
| AUTH-02 | rag-engine 请求缺 tenant/授权过滤/policyVersion | contract/security | fail-closed，返回稳定 4xx，不执行检索 | not-run |
| AUTH-03 | ACL/部门/成员撤权后查询缓存 | integration | 在传播 SLA 内所有访问面失效，旧引用重新授权失败 | not-run |
| AUTH-04 | 有 view_content、无 download_original | API/E2E | 可受控预览，不可获取原始下载流 | not-run |
| TENANT-01 | 伪造其他租户 KB/document/user 关联写入 | DB/integration | 复合约束或策略拒绝，数据库无污染 | not-run |
| TENANT-02 | admin/批处理/异步消费者绕过常规 ORM | security | 仍受 tenant 范围和审计约束 | not-run |
| OIDC-01 | Code + PKCE 正常登录、刷新、登出 | E2E | state/nonce/aud/iss 有效，refresh 轮换 | not-run |
| OIDC-02 | password grant 请求 | contract/security | 端点/授权类型不可用 | not-run |
| SVC-01 | 无工作负载凭证或伪造 server 身份调用 engine | integration | 401/403，NetworkPolicy 不是唯一边界 | not-run |
| INDEX-01 | PENDING_REVIEW/REJECTED/disabled/旧版本内容 | retrieval | 搜索与问答均零召回 | not-run |
| INDEX-02 | ACL/状态事件更新索引失败 | resilience | 二次授权阻止泄漏并触发告警/暂停 | not-run |
| INDEX-03 | 1024→3072 维 embedding 换模 | migration | 新索引构建/评测/别名切换，旧索引可回滚 | not-run |
| API-01 | OpenAPI 与文档摘要/三端模型对账 | CI | 无缺失路径、字段、枚举、权限和错误 | not-run |
| API-02 | 所有写命令重复提交 | contract/integration | 幂等结果一致，无重复文档/索引/事件 | not-run |

## 2. 摄取、连接器与治理

| ID | 场景 | 期望 | 状态 |
| --- | --- | --- | --- |
| INGEST-01 | MIME 伪造、宏、恶意文件、ZIP bomb | 内容保持隔离，不进入解析/索引，事件可审计 | not-run |
| INGEST-02 | 文档内含间接提示词注入 | 内容作为不可信数据，不改变系统策略或触发动作 | not-run |
| DLP-01 | 高敏文档配置禁止外部模型 | 路由只选允许的本地/区域 provider；无可用 provider 时安全失败 | not-run |
| SYNC-01 | 相同外部 ID/ETag 重复同步 | 不产生重复版本、切片或事件 | not-run |
| SYNC-02 | 源更新/重命名/移动 | 保持稳定血缘，仅更新受影响版本和元数据 | not-run |
| SYNC-03 | 源删除或撤权 | 在 SLA 内从查询、预览、缓存和引用访问中消失 | not-run |
| SYNC-04 | 429/网络中断/游标过期 | 退避重试、游标恢复、无丢失/重复，可单对象重放 | not-run |
| GOV-01 | 必填元数据或所有者缺失 | 不得发布，返回可定位字段错误 | not-run |
| GOV-02 | 到期复审/文档替代 | 旧内容下线或标记 stale，答案指向当前权威版本 | not-run |
| GOV-03 | 法律保全下请求删除 | 删除被拒绝并审计；解除后按审批流程处置 | not-run |
| GOV-04 | 删除完成 | 对象、索引、缓存、在线副本产生可核验删除证明 | not-run |

## 3. RAG 质量与 AI 安全

- 黄金集按业务域、语言、文档类型、权限角色和时间敏感性分层，测试数据不使用未脱敏生产内容。
- 检索指标：Recall@K、MRR/nDCG、Context Precision/Recall、ACL 泄漏率必须为 0。
- 生成指标：Faithfulness、Answer Relevancy、引用正确率、拒答准确率、过期信息率和敏感泄漏率。
- 安全集：直接/间接提示词注入、投毒、向量碰撞/异常、系统提示泄漏、PII/密钥泄漏、XSS/恶意链接、资源耗尽。
- 回归维度：parser/OCR/embedding/reranker/LLM/prompt/index profile 任一版本变化都需与冻结基线比较。
- 发布门禁：P0 安全用例零失败；质量阈值按业务域冻结，不能只看全局平均；性能和成本退化需有批准记录。

## 4. 可靠性、性能与灾备

| ID | 场景 | 期望 | 状态 |
| --- | --- | --- | --- |
| REL-01 | outbox 重复投递/消费者重启 | 至少一次投递但业务只生效一次 | not-run |
| REL-02 | PostgreSQL/Redis/对象存储/索引/模型单点故障 | 符合已定义降级，授权和数据分类不得降级 | not-run |
| REL-03 | connector/webhook 死信重放 | 可审计、可限速、无重复副作用 | not-run |
| PERF-01 | 多租户混合负载与热点租户 | 配额隔离生效，其他租户 SLO 不被拖垮 | not-run |
| PERF-02 | 百万级授权文档过滤 | P95 达标且不通过截断白名单放宽权限 | not-run |
| DR-01 | PG 时间点恢复 + 对象恢复 + 索引重建 | 在 RPO/RTO 内恢复，数量/版本/引用校验一致 | not-run |
| DR-02 | 误发布索引/模型配置 | 原子回切旧别名和配置，历史任务不覆盖回滚结果 | not-run |

## 5. 最终交付证据

- 精确记录 commit/镜像/契约/migration/index profile/prompt/provider 版本。
- 保存 build、unit、integration、E2E、OpenAPI lint、migration dry-run、安全扫描、RAG eval、压测和恢复演练输出。
- 用 `passed / failed / not-run / blocked` 分列结果；外部依赖或环境未提供时说明风险，不以 mock 替代验收。
- 安全测试证据脱敏；禁止在报告中保留 token、API Key、原文敏感片段、私网地址和生产账号。

