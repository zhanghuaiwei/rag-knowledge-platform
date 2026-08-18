# integration 模块（外部集成）

> 范围：事务性 outbox（OutboxPort/OutboxAppender）+ Webhook 投递引擎（签名 / 重试 / 死信 / 重放）。

## 模块结构

| 层 | 内容 |
| --- | --- |
| `service/` | `IntegrationUseCase` 接口（手动投递 / 死信重放）+ `impl/WebhookDeliveryServiceImpl`（投递引擎 + 轮询器）、`impl/OutboxAppender`（业务事务内追加事件） |
| `port/` | `OutboxPort`（事件追加）、`WebhookSenderPort`（HTTP 发送抽象，单测可注入内存桩） |
| `adapter/` | `WebhookHttpClient`（RestClient 实现，10s 超时，异常全部转为失败结果不上抛） |
| `persistence/` | outbox_event 实体与 Mapper + `WebhookDeliveryQueueMapper`（投递队列 SQL 直连 webhook_subscription/webhook_delivery） |

## 功能文档

- [design/webhook-delivery-engine.md](design/webhook-delivery-engine.md) —— 签名方案 / 重试与死信 / 投递时序

## 关键约定

- **事务边界**：业务事件与 outbox 写入同事务（`OutboxAppender`，REQUIRED 传播）；投递引擎只轮询，**HTTP 调用绝不放进数据库事务**。
- **at-least-once**：`uq_webhook_delivery_event (tenant_id, subscription_id, event_id)` 唯一约束 + `ON CONFLICT DO NOTHING` 保证重复 fan-out 不产生重复投递行；订阅方需以 `X-RagKB-Event-Id` 去重。
- **跨模块表访问**：webhook 两表的实体属 admin 模块；本模块按 identity 模块 `UserAccountMapper` 直连 `sys_org/audit_log` 的先例，以自建 Mapper + 手写 SQL 直连（不建跨模块 Java 依赖，PackageStructureTest 约束允许）。
