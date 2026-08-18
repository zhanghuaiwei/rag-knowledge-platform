# admin 模块设计：组织目录 / 安全审计 / 站内通知

> 实现文件：`service/src/main/java/com/ragkb/service/modules/admin/service/impl/AdminServiceImpl.java`
> 契约对齐：`web/api-client/types/admin.ts`、`web/api-client/contracts/admin.ts`

## 1. 组织目录（sys_org / sys_user_org）

### 1.1 数据模型要点

- `sys_org` 为自引用树（`parent_id` 外键），带物化路径 `path`（形如 `/1/3/`，DDL 约束 `LIKE '/%'`）。
- 同级名称唯一由数据库 `uq_sys_org_sibling_name UNIQUE NULLS NOT DISTINCT (tenant_id, parent_id, name)` 兜底，应用层先做可读预检（409）。
- 成员挂靠关系在 `sys_user_org`（m2m），对组织有 `ON DELETE CASCADE` 外键。

### 1.2 实现规则

| 用例 | 规则 |
| --- | --- |
| listOrgs | 平铺返回（前端按 `parentId` 自行组树）；`memberCount` 一次 GROUP BY 聚合（`SysOrgMapper.selectMemberCounts`），避免 N+1 |
| createOrg | `parentId=null` 建根节点；父组织必须存在且 ACTIVE；同级重名预检 + 唯一约束兜底（409）；插入后回填物化路径 `path = 父path + <id> + "/"` |
| updateOrg | PATCH 语义：`parentId=null` 表示本次不移动（前端契约只传 name）；改名做同级唯一（排除自身）；移动时校验新父「存在 / ACTIVE / 非自身 / 非后代」，通过 `replaceSubtreePath` 一条 SQL 重写整棵子树的物化路径前缀（防环判定依据：新父 path 以本组织 path 为前缀即成环） |
| deleteOrg | 守卫：`sys_user_org` 有引用 → 409；有子组织 → 409；**物理删除**（`hardDeleteById`） |

### 1.3 权衡说明

- **组织删除为何是物理删而非软删**：`uq_sys_org_sibling_name` 唯一约束不含 `del_flag` 列，软删行会永久占住 `(tenant_id, parent_id, name)` 槽位，导致同名组织无法重建。对齐 identity 模块 `hardDeleteByTenantAndUser` 的处理理由。删除守卫（无成员/无子组织）由服务层前置校验，`ON DELETE CASCADE` 仅兜底。
- **幂等键**：`createOrg/createWebhook` 的 `Idempotency-Key` 头已接收但项目幂等设施（`idempotency_record`）尚未接线到用例层，与 `KbServiceImpl.createKb` 的现状一致（遗留点，见 §4）。

## 2. 安全审计（audit_log）

### 2.1 查询（listAuditLogs）

- 分页（page/size，size 上限 100），过滤参数：`action`（模糊）、`resourceType`（模糊）、`actorId`（精确，`actor_id` 为 VARCHAR 列做字符串化匹配）、`result`（精确：SUCCEEDED/DENIED/FAILED）、`dateFrom`/`dateTo`（ISO-8601，解析失败返回 400 而非静默全量）。
- 排序：`occurred_at DESC`（走 `idx_audit_time` 索引）。
- **租户列说明**：`audit_log` 表有 `tenant_id NOT NULL` 列，因此查询带租户过滤（红线要求按表结构实际情况执行，本表满足条件）。

### 2.2 写入（admin 写操作审计）

- 组织与 Webhook 的全部写操作（create/update/delete/toggle）在同事务内经 `AuditLogMapper.insert` 写一条审计：
  - `actor_type='USER'`、`actor_id=<当前登录用户全局id>`、`action` 形如 `org.create` / `webhook.toggle`、`resource_type` = `ORG` / `WEBHOOK`、`result='SUCCEEDED'`、`occurred_at=now()`。
  - `detail` 列（JSONB NOT NULL DEFAULT '{}'）不设值，走数据库默认（MyBatis-Plus 插入 null 字段不落 SQL）。
- identity 模块写审计走 `UserAccountMapper.insertAuditLog` SQL 直连（不跨模块依赖 admin 持久化）；admin 模块自建 `AuditLogMapper` 后直接用实体 insert，两侧写入格式一致。

## 3. Webhook 订阅管理（webhook_subscription）

### 3.1 用例规则

| 用例 | 规则 |
| --- | --- |
| listWebhooks | 当前租户全部未删除订阅，按 id 倒序；**secret 不回显** |
| createWebhook | `targetUrl` 必须 `https://`（对齐 DDL `ck_webhook_target_https`，应用层先拦截给出可读错误）；订阅名租户内唯一（`uq_webhook_subscription_name`）；`eventTypes` 去重序列化为 JSON 数组，经 `insertWithJsonb` 的 `CAST(? AS jsonb)` 写入；生成 `whsec_` 前缀 256bit 随机密钥存 `secret_ref`，**仅在创建响应返回一次** |
| toggleWebhook | `paused=true → PAUSED`，`false → ACTIVE`；乐观锁（row_version）并发保护 |
| deleteWebhook | 软删：`status=REVOKED` + `del_flag=1` |
| listWebhookDeliveries | 分页 + status 过滤（PENDING/SENDING/SUCCEEDED/RETRY/DEAD），id 倒序 |

### 3.2 secret 存储与 VO 字段说明

- **为何不能像 API Key 只存哈希**：投递引擎计算 HMAC-SHA256 签名时必须恢复密钥明文，哈希无法参与运算。因此 `secret_ref` 列保存明文（`whsec_` + 32 字节 CSPRNG 的 base64url，≥256bit 熵），日志与查询接口绝不透出。
- **WebhookVo 新增 `secret` 字段的原因**：前端契约 `CreateWebhookInput → Webhook` 未定义 secret 返回位，但订阅方必须拿到密钥才能验签（否则 Webhook 功能不可用）。参考 `ApiKeyCreatedVo`（`{key, secret}`）的一次性返回模式，在 `WebhookVo` 增加 nullable 的 `secret` 字段——仅 `createWebhook` 响应非空，其余接口恒为 null；前端 TS interface 未含该字段，运行时多字段不影响反序列化。**若后续契约收敛，建议对齐 ApiKey 增加 `WebhookCreatedVo`。**
- **deleteWebhook 为何软删**：`webhook_delivery` 对 subscription 的外键是 `ON DELETE CASCADE`，物理删会连带抹掉投递历史（排障证据）。代价：软删行占住 `(tenant_id, name)` 唯一键，同名订阅需换名重建。
- **生产加固建议**：`secret_ref` 明文落库是当前表结构（单列引用）下的最小可行方案；接入外部 KMS / secret 管理器后应改为存引用句柄，由引擎运行时取密钥。

### 3.3 投递侧

投递引擎（签名 / 重试 / 死信 / 轮询）属 integration 模块，见
[../../integration/design/webhook-delivery-engine.md](../../integration/design/webhook-delivery-engine.md)。

## 4. 站内通知（notification）

- `listNotifications`：当前登录用户（全局 id）+ 租户双重过滤，最近 100 条（契约为不分页 List）。
- `markNotificationRead`：tenant + userId 双过滤更新，0 行按 404（防越权操作他人通知）。
- `markAllNotificationsRead`：仅命中 `read=false` 行，幂等。
- 注意：通知的产生方（任务完成 / 审核待办等事件源写 `notification` 表）不在本任务范围，当前实现只覆盖读取与已读管理。

## 5. 遗留风险

| 项 | 说明 |
| --- | --- |
| 幂等设施未接线 | `Idempotency-Key` 头已接收未生效（项目无先例），重复提交靠唯一约束兜底 |
| webhook 同名重建 | 软删占住唯一键，需换名（§3.2 权衡） |
| 通知无产生方 | `notification` 表暂无写入方，需后续任务/审核流程接入 |
| AdminService 单测未补 | 本次交付投递引擎单测（integration），admin 侧建议后续按 UserAccountServiceImplTest 模式补齐 |
