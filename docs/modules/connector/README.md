# Connector 内容源连接器模块

## 模块定位

连接器（connector）负责把外部内容源接入平台：管理源端连接配置（`source_connection`）、
按需校验连通性、触发与跟踪同步任务（`sync_job`），并把外部对象清单增量收敛到
`source_object`（按外部 id upsert + 删除墓碑）。它是「外部世界 → 平台文档体系」的
入口层，后续由摄取链路把 `source_object` 转换为 `document` 入库（本轮 fail-closed 未实现）。

## 功能总览

| 能力 | 端点 | 实现 |
| --- | --- | --- |
| 连接器列表 | `GET /api/v1/connections` | `ConnectorServiceImpl#listConnectors`（租户过滤 + 最近一轮同步计数） |
| 创建连接器 | `POST /api/v1/connections` | 按 provider_key 白名单 + config 必填字段校验后落库 |
| 连接器详情 | `GET /api/v1/connections/{id}` | 归属校验 + 视图聚合 |
| 更新连接器 | `PATCH /api/v1/connections/{id}` | PATCH 语义（名称唯一预检 / config 整体替换再校验 / enabled↔status） |
| 删除连接器 | `DELETE /api/v1/connections/{id}` | 置 REVOKED + 软删，级联取消未完结同步任务 |
| 连通性校验 | `POST /api/v1/connections/{id}/validate` | 静态字段校验 + 适配器真实探测；未接入类型 fail-closed |
| 触发同步 | `POST /api/v1/connections/{id}/sync` | 受理即返回 202 Task；登记 sync_job=QUEUED |
| 同步任务详情 | `GET /api/v1/sync-jobs/{jobId}` | 状态 + 计数 + 失败对象明细 |
| 取消同步 | `POST /api/v1/sync-jobs/{jobId}` | QUEUED/RUNNING → CANCELLED（条件更新防覆盖） |

## 支持的连接器类型

| provider_key | 必填 config 字段 | 适配器 | 说明 |
| --- | --- | --- | --- |
| `local_directory` | `rootPath` | ✅ `LocalDirectoryConnector` | 扫描本地目录清单（开发/内网文件共享） |
| `http_index` | `indexUrl` | ✅ `HttpIndexConnector` | GET 一个 JSON 索引 URL（内网清单服务） |
| `sharepoint` | `siteUrl` | ❌ 未接入 | 配置可保存；校验/同步明确报「暂不支持」 |
| `confluence` | `baseUrl`、`space` | ❌ 未接入 | 同上 |
| `s3` | `bucket`、`region` | ❌ 未接入 | 同上 |

fail-closed 原则：未接入 SDK 的类型**不假报成功**——创建仅做静态字段校验，
连通性校验返回 `ok=false`（「暂未接入适配器」），同步入口直接 400 拒绝入队，
同步执行器对漏网任务兜底置 `FAILED(ADAPTER_NOT_IMPLEMENTED)`。

## 关键约定

- **多租户隔离**：所有读写经 `requireConnection` / 任务查询显式 `tenant_id` 过滤，
  跨租户访问按不存在或无权处理（deny-by-default）。
- **归属库**：契约 DTO 暂缺 `kbId` 字段，约定 `config.kbId`（可选数字）声明归属，
  缺省兜底默认库 1；PATCH 更新不改变归属。契约冻结时应上移为一级字段。
- **凭证红线**：config 可能含密码/token——日志与异常消息只报字段名，绝不回显取值；
  `secret_ref` 预留（secret 管理模块未建，当前凭证随 config JSONB 落库，见遗留风险）。
- **状态机**（对齐 DDL CHECK）：连接器 `ACTIVE/PAUSED/ERROR/REVOKED`，仅 ACTIVE 可同步；
  任务 `QUEUED/RUNNING/PARTIAL/SUCCEEDED/FAILED/CANCELLED`，终态写回前条件校验
  `status=RUNNING`，保证用户取消不被执行器覆盖。
- **装配开关**：适配器与调度器按 `ragkb.db.enabled=true` 装配（依赖 Mapper）；
  `ConnectorServiceImpl` 无条件注册（Controller 未条件化），db 关闭时调用明确报
  「数据访问未启用」而非启动失败。

## 更多文档

- 业务流程 / 数据流转 / 适配器扩展指南：[design/connector-sync.md](design/connector-sync.md)
