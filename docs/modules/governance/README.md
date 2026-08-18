# governance 治理中心模块

治理中心承载平台的内容治理能力，覆盖四条业务流程：**元数据 schema 管理**、**内容审核**、**保留策略与法律保全**、**删除审批与删除证明**。

- 后端入口：`service/src/main/java/com/ragkb/service/modules/governance/`
- 权威契约：`web/api-client/types/governance.ts`（前端契约）+ `deploy/ddl/init.sql`（存储层 CHECK 约束）
- 详细设计（状态机 / 数据流转图 / 边界条件）：[design/governance-flows.md](design/governance-flows.md)

## 模块结构

```
modules/governance/
├── controller/          # 4 个轻量 Controller（只做参数绑定，业务在 Service）
│   ├── MetadataSchemaController.java        # /api/v1/metadata-schemas
│   ├── ReviewController.java                # /api/v1/reviews、/api/v1/documents/{id}/withdraw
│   ├── RetentionAndLegalHoldController.java # /api/v1/retention-policies、/api/v1/legal-holds
│   └── DeletionController.java              # /api/v1/deletion-tasks、/api/v1/deletion-receipts
├── service/
│   ├── GovernanceService.java               # 用例接口（16 个方法）
│   └── impl/
│       ├── GovernanceServiceImpl.java        # 真实实现（ragkb.db.enabled=true）
│       └── GovernanceServiceScaffoldImpl.java # scaffold 降级桩（db.enabled=false，端点 501）
├── persistence/          # 本模块自有表（entity + mapper）
└── dto/ vo/              # 契约出入参
```

## API 端点一览

| 域 | 端点 | 语义 |
| --- | --- | --- |
| 元数据 | `GET/POST /metadata-schemas`、`POST /metadata-schemas/{id}/publish` | schema 列表 / 新建草稿 / 发布 |
| 审核 | `GET /reviews`、`POST /reviews/{documentId}/approve|reject`、`POST /documents/{id}/withdraw` | 待审队列 / 通过 / 驳回 / 撤回 |
| 保留 | `GET/POST /retention-policies`、`PATCH /retention-policies/{id}` | 策略列表 / 新建 / 启停 |
| 保全 | `GET/POST /legal-holds`、`POST /legal-holds/{id}/release` | 保全列表 / 创建 / 解除 |
| 删除 | `GET /deletion-tasks`、`POST /deletion-tasks/{id}/approve`、`GET /deletion-receipts` | 任务队列 / 审批执行 / 删除证明 |

## 跨模块协作（PackageStructureTest 红线约束）

治理模块**只经 Service/Port 触达其他模块**，不直连其持久化层：

- `DocumentService`：审核状态流转（approve/reject/withdraw）、待审队列数据、文档治理快照、按 id 批量软删；
- `KbService`：审核队列与删除任务的库名回填（`kbNamesByIds`）；
- `UserAccountService`：提交人 / 操作人 / 保全创建人的显示名回填（`displayNamesOf`）；
- `RagEnginePort`：删除审批通过后清理文档向量（`deleteVectors`）。

## 多租户与安全

- 所有查询带 `tenant_id` 过滤 + `del_flag=0`（`@TableLogic` 全局生效）；
- 按资源 id 的操作先经 `requireXxx` 做「存在 + 租户归属」校验（deny-by-default，跨租户按不存在拒绝）；
- 危险操作（`approveDeletion`）执行前强制校验目标文档的法律保全冲突，命中即阻断（任务转 BLOCKED）；
- 写操作 `@Transactional`，审计字段（create_by/update_by 等）由 `AuditMetaObjectHandler` 自动填充。

## 装配说明

`GovernanceServiceImpl` 依赖 MyBatis Mapper，仅在 `ragkb.db.enabled=true` 时注册；`GovernanceServiceScaffoldImpl` 在 `db.enabled=false`（或未配置）时注册，端点保持 501。4 个 Controller 无条件装配，两种模式下均能注入唯一实现（对齐 access 模块 `DenyByDefaultAccessPolicy` 的互补装配先例）。
