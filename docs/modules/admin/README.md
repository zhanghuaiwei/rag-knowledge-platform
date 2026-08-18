# admin 模块（管理中心）

> 范围：组织目录 / 安全审计 / Webhook 订阅管理 / 站内通知。
> 成员账号管理已迁 identity 模块（V0.5），本模块不涉及用户凭据。

## 模块结构

| 层 | 内容 |
| --- | --- |
| `controller/` | OrgController、AuditLogController、WebhookController、NotificationController（契约入口，无业务逻辑） |
| `service/` | `AdminService` 接口 + `impl/AdminServiceImpl`（14 个用例实现） |
| `persistence/` | sys_org / sys_user_org / audit_log / webhook_subscription / webhook_delivery / notification 六张表的实体与 Mapper |
| `dto/` `vo/` | 契约入参与响应视图（对齐 `web/api-client/types/admin.ts`） |

## 功能文档

- [design/admin-organization-audit-notification.md](design/admin-organization-audit-notification.md) —— 组织 CRUD / 审计查询 / 通知的实现规则与权衡

## 关键约定

- **多租户隔离**：所有读写按 JWT 推导的 `tenantId` 过滤（deny-by-default）；按主键定位的资源跨租户访问统一按 404 处理。
- **安全审计**：组织与 Webhook 写操作同事务写 `audit_log`（动作码 `org.*` / `webhook.*`），不落任何敏感值。
- **Webhook secret**：创建时生成 `whsec_` 前缀 256bit 密钥存 `secret_ref` 列，仅创建响应返回一次；投递侧实现见 integration 模块。
- **JSONB 列**：`webhook_subscription.event_types` 经 XML 的 `CAST(? AS jsonb)` 插入（BaseMapper.insert 会按 varchar 写入报类型错）。
