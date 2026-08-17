# analytics 模块（用量与质量）

后端实现：`service/src/main/java/com/ragkb/service/modules/analytics/`
接口入口：`AnalyticsController`（`/api/v1/analytics`），业务实现：`AnalyticsServiceImpl`。

## 模块定位

为前端「质量与用量」页（`web/app/(main)/analytics/page.tsx`）提供分层指标：

| 端点 | 方法 | 语义 |
| --- | --- | --- |
| `GET /api/v1/analytics/usage?period=DAY\|WEEK\|MONTH` | `getDailyUsage` | 按日/周/月桶聚合问答用量（量 / 无答案 / 低置信 / token / 活跃用户 / 成本） |
| `GET /api/v1/analytics/costs?period=DAY\|WEEK\|MONTH` | `getTokenCosts` | 按模型聚合 Token 与成本 |
| `GET /api/v1/analytics/top-documents` | `getTopDocuments` | 近 30 天被回答引用最多的文档 TOP 10 |
| `GET /api/v1/analytics/dau` | `getDau` | 近 14 天日活跃用户（去重） |
| `GET /api/v1/analytics/kb-health` | `getKnowledgeHealth` | 租户级健康度四指标 |
| `GET /api/v1/analytics/export?kind=...&period=...` | `export` | CSV 导出（UTF-8 BOM，RFC 4180） |

## 事实源与表

| 表 | 用途 | 说明 |
| --- | --- | --- |
| `chat_message` | 问答量 / 无答案 / 低置信 / token / 质量比率 | 一条 `role='ASSISTANT'` 消息计一次问答；`answer_status` 枚举 `NO_ANSWER` / `LOW_CONFIDENCE` |
| `chat_session` | 活跃用户归属 | `chat_message` 无 `user_id`，JOIN 会话取归属用户 |
| `chat_message_source` | 热门文档 | 回答引用来源明细（`message_id` / `document_id`） |
| `cost_record` | Token 与成本 | 全场景（EMBEDDING/RERANK/LLM/OCR）计费明细；**当前无写入方，查询返回真实空结果** |
| `document` | 文档新鲜度 | `lifecycle_status='ACTIVE'` 在库文档近 90 天更新占比 |
| `kb` | 热门文档展示字段 | JOIN 回填知识库名（LEFT JOIN，库删除不丢统计） |

> `usage_daily` 为设计上的预汇总表，当前同样无写入方；实现直接从明细表实时聚合，
> 后续接入离线汇总后再切换事实源（口径不变）。

## 详细设计

- [design/analytics-backend-implementation.md](design/analytics-backend-implementation.md)：
  各接口实现逻辑、SQL 聚合、数据流转图、边界条件（空数据 / 租户隔离 / 权限）。

## 通用约定（红线）

- **多租户隔离**：所有 SQL 均带 `tenant_id = 当前租户`（JWT 主体；dev/API Key/未认证兜底默认租户 1，与 `KbServiceImpl` 一致）。
- **逻辑删除**：手写 XML 显式 `del_flag = 0`（`@TableLogic` 不作用于手写 SQL）。
- **审计列名**：对齐 `V0.3__unified_audit_columns.sql`（`create_time` / `update_time`）。
- **不造假数据**：无事实源的字段返回真实 0 / 空列表（如 `searchCount` 无搜索日志表、`cost_record` 空表）。
- **统计时区**：`Asia/Shanghai`（TIMESTAMPTZ 先转本地时间再 `date_trunc`，保证按日边界正确）。
