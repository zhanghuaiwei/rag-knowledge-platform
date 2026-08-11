# 企业通用化后端任务

> **状态**：待评审 · **版本**：v0.2-draft · **负责人**：待指定 · **最近更新**：2026-08-10
> **需求**：[`../../requirements/README.md`](../../requirements/README.md) · **设计**：[`../../design/README.md`](../../design/README.md)

## 前置门禁

- [ ] 明确 SaaS/私有化交付形态、身份模型和 ACL allow/deny 语义。
- [ ] 完成 OpenAPI 差距评审，冻结 Query/Search 授权上下文和异步任务通用结构（`server.openapi.yaml` 为 v0.2 草稿，评审中未冻结）。
- [ ] 完成数据库迁移、回滚、租户约束和索引元数据评审。
- [x] 配置真实 backend build/test 命令（`mvn -B test / package`），工程可构建可启动。

## Gate 0：阻断问题修复

- [ ] 建立 `PolicyDecisionPoint` 和不可伪造的 `SubjectContext`；所有 Controller/Service 使用资源级授权，不仅检查角色名（当前接口入口骨架未含 PDP 实现）。
- [ ] 为问答、搜索、预览、下载、引用和导出接入同一授权过滤；rag-engine 缺少授权上下文时 fail-closed（`RagEnginePort`/`RagEngineHttpClient` 已定义，未实现调用）。
- [ ] 完成索引字段与状态事件：tenant/kb/document/version/publish/disabled/policy/fileType/time/indexProfile。
- [ ] 将浏览器认证迁移到 OIDC Code + PKCE；加入 issuer/audience/nonce/state/token replay 校验（`RAGKB_AUTH_MODE=oidc` 模式开关已接入 Spring Security OIDC；真实 IdP 对接与 token 校验待人工完成）。
- [ ] 为 server ↔ rag-engine 配置工作负载身份认证与密钥轮换，保留 NetworkPolicy 作为第二层防护。
- [x] machine-readable OpenAPI 已补齐为 v0.2 草稿并覆盖已实现端点（89 路径/113 操作，见 `../api/server.openapi.yaml`）；模型生成与 CI 契约漂移检查待补。
- [ ] 修复 tenant 关系完整性、枚举/范围约束、组织树和用户身份模型；引入正式 schema migration（v0.2 DDL 已含复合外键与 CHECK，Flyway 未接入）。
- [ ] 实现不可变 `indexProfileVersion` 与全量重建/别名切换，禁止请求时自动切换 embedding 空间。

## 企业 MVP

- [ ] 建立 quarantine → malware/DLP/secret/injection scan → parse 的摄取流水线；扫描状态与审核状态分离。
- [ ] 建立 source/provenance、元数据 schema、分类、所有者、复审日期和权威版本模型。
- [ ] API Key 增加 scope、allowed KB、期限、last used、revoke 和 peppered digest；所有调用进入审计（Controller/DTO 已落地，`AuthService` stub）。
- [ ] 使用 transactional outbox 发布文档/ACL/审核/索引事件；消费者幂等、可重放、有死信与可观测积压。
- [ ] 按租户实施存储、上传、同步、查询、token、并发和导出配额，超限返回稳定错误。
- [ ] 记录检索配置、策略版本、文档版本、引用和模型版本，支持问题级追溯与重新评估。

## 通用化扩展

- [ ] 提供 `ContentConnector` SDK 和 contract tests；先实现 upload/object-storage、SharePoint/OneDrive、Confluence（`ContentConnectorPort` 已定义）。
- [ ] 支持增量游标、Webhook、ACL/组映射、删除墓碑、新鲜度 SLA、限流退避和单对象重放。
- [ ] 接入 SCIM 用户/组生命周期；同步撤权触发策略缓存和索引策略更新。
- [ ] 实现分类/保留/法律保全/审批删除/删除证明；对象、索引、缓存和在线副本统一处置。
- [ ] provider registry 暴露格式、维度、区域、成本、数据用途和健康能力；模型路由只能在策略允许集合内选择。
- [ ] Webhook 投递增加 SSRF 防护、egress allowlist、签名轮换、delivery log、指数退避和 dead letter。

## 完成定义

- [ ] 每个任务引用已确认 OpenAPI 版本和 migration 版本。
- [ ] 单元测试覆盖策略组合、状态机、幂等、重试与边界；集成测试使用真实 PostgreSQL/Redis/对象存储/索引。
- [ ] 跨租户、跨库、文档 ACL、历史引用、缓存和内部伪造请求均有负向测试。
- [ ] 构建、测试、OpenAPI lint、migration dry-run、依赖/镜像/SBOM/secret scan 均有执行证据。
- [ ] 数据、配置、权限、部署、兼容和回滚影响已记录，不把 mock/计划写成验收通过。

> **实现现状备注（2026-08）**：上述勾选反映"接口入口骨架 + 基础设施开关"阶段已完成项；所有业务用例在 `service/application/impl/*.java` 中仍为 `NotYetImplemented.stub`（HTTP 501 `E-9998`），属人工实现点，见 `service/README.md`。

