# API 契约

> **文档状态**：草稿 · 版本：v0.1.0 · 最近变更：2026-08-10
> **引用约定**：**端点为本文档唯一权威源**（机器可读见 `api/server.openapi.yaml` 与 `api/rag-engine.openapi.yaml`，以 YAML 为准）；类/接口→03；错误码→03§9；表名→04。
> **关联文档**：`01-需求分析`、`03-详细设计`、`08-测试与质量评估`。

> **⚠️ 文档状态：已废弃（v0.1）——人工摘要，请勿作为实现依据**
>
> 本文档是人工摘要，路径与字段落后于 v0.2 设计，且与 `api/*.openapi.yaml` 对账不一致（摘要 49 个唯一路径 vs YAML 22 个，至少 27 个摘要路径不存在于 YAML）。缺失 OIDC 登录、授权上下文、连接器、治理、索引构建、删除证明、scoped API Key 等 v0.2 能力。
> 唯一权威机器契约是 `docs/api/*.openapi.yaml`。**`server.openapi.yaml` 已升级为 v0.2 草稿（评审中，未冻结）；`rag-engine.openapi.yaml` 仍是 v0.1**。在 v0.2 契约评审完成前，请勿按本文档或旧 YAML 实现。详见通用化 requirements P0-4。

---

## 1. 契约约定

- **JSON 字段**：`camelCase`。
- **时间**：ISO 8601 UTC（`2026-08-10T09:00:00Z`）。
- **分页**：请求 `page`（从 1）+ `size`；响应 `items / total / page / size / hasMore`。
- **错误响应**：`{ code, message, requestId, timestamp, details }`；错误码字典见 `03§9`（E-xxxx）。
- **鉴权**：
  - 人机调用：`Authorization: Bearer <accessToken>`。
  - 机器调用：`Authorization: ApiKey <key>`（仅限问答类端点）。
  - 除注册、登录、刷新、健康检查外均需认证。
- **版本**：`/api/**` 无显式版本前缀；不兼容变更升契约版本（见 §8）。

---

## 2. 前端 ↔ Java 端点

> 完整机器可读定义：`api/server.openapi.yaml`。

### 2.1 认证与 API Key（F2.6）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/oauth2/token` | POST | password 模式签发 TokenPair | 400/401/429 | 公开（限流 10 次/5min/IP） |
| `/oauth2/token/refresh` | POST | refreshToken 轮换 | 400/401 | 公开 |
| `/oauth2/token/revoke` | POST | 登出（refreshToken 进黑名单） | 400/401 | 已认证 |
| `/api/admin/api-keys` | POST | 创建 API Key（返回明文一次） | 400/401/403 | ADMIN |
| `/api/admin/api-keys` | GET | 列表（不含明文） | 401/403 | ADMIN |
| `/api/admin/api-keys/{id}` | DELETE | 吊销 | 401/403/404 | ADMIN |

**TokenPair**：

```json
{ "accessToken": "...", "accessExpiresIn": 7200,
  "refreshToken": "...", "refreshExpiresIn": 604800, "tokenType": "Bearer" }
```

### 2.2 知识库（F2.1）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/kb` | GET | 分页列表（含成员角色） | 400/401 | 已认证 |
| `/api/kb` | POST | 创建 | 400/401/409 | 已认证 |
| `/api/kb/{id}` | GET | 详情 | 401/403/404 | 成员 |
| `/api/kb/{id}` | PATCH | 编辑配置 | 400/401/403/404 | OWNER |
| `/api/kb/{id}` | DELETE | 软删除 | 401/403/404 | OWNER |
| `/api/kb/{id}/clone` | POST | 克隆（异步，返回 202 + taskId） | 401/403/404 | OWNER |
| `/api/kb/{id}/members` | GET | 成员列表 | 401/403/404 | OWNER |
| `/api/kb/{id}/members` | POST | 添加成员 `{userId, role}` | 400/401/403/404 | OWNER |
| `/api/kb/{id}/members/{userId}` | DELETE | 移除成员 | 401/403/404 | OWNER |

**创建请求示例**：

```json
{ "name": "后端规范库", "description": "团队后端开发规范",
  "visibility": 0, "embeddingModel": "bge-m3",
  "chunkSize": 512, "chunkOverlap": 50, "topK": 5, "rerankerEnabled": true }
```

### 2.3 文档（F2.2）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/documents/upload/init` | POST | 分片上传初始化（含指纹秒传） | 400/401/413 | EDITOR+ |
| `/api/documents/upload/{uploadId}/parts/{partNumber}` | PUT | 上传分片（5MB，multipart） | 400/401/409 | EDITOR+ |
| `/api/documents/upload/{uploadId}/complete` | POST | 合并并触发解析 | 400/401/409 | EDITOR+ |
| `/api/documents/upload/{uploadId}` | GET | 断点续传查询（已传分片） | 400/401/404 | EDITOR+ |
| `/api/kb/{kbId}/documents` | GET | 文档列表（含解析状态，按状态过滤） | 400/401/403 | 成员 |
| `/api/documents/{id}` | GET | 文档详情 | 401/403/404 | 成员 |
| `/api/documents/{id}/retry` | POST | 重新解析 | 401/403/404/409 | EDITOR+ |
| `/api/documents/{id}/disable` | PATCH | 禁用/启用（`{disabled:true}`） | 401/403/404 | OWNER |
| `/api/documents/{id}` | DELETE | 软删除 | 401/403/404 | OWNER |
| `/api/documents/{id}/versions` | GET | 版本列表 | 401/403/404 | 成员 |
| `/api/documents/{id}/versions/{versionNo}/rollback` | POST | 回滚到指定版本 | 401/403/404/409 | EDITOR+ |
| `/api/kb/{kbId}/documents/batch-zip` | POST | ZIP 批量导入（multipart） | 400/401/413 | EDITOR+ |

**解析状态枚举**：`UPLOADING | PARSING | SPLITTING | EMBEDDING | READY | FAILED`（权威见 `03§5.1`）。

### 2.4 问答（F2.3）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/chat/sessions` | GET | 会话列表（分页） | 400/401 | 已认证 |
| `/api/chat/sessions` | POST | 创建会话 `{title?, kbIds}` | 400/401 | 已认证 |
| `/api/chat/sessions/{id}` | PATCH | 归档/重命名 | 400/401/404 | 所有者 |
| `/api/chat/sessions/{id}` | DELETE | 逻辑删除 | 401/404 | 所有者 |
| `/api/chat/sessions/{id}/messages` | GET | 历史消息（分页） | 400/401/404 | 所有者 |
| `/api/chat/messages` | POST | **发起问答（SSE 流式）** | 400/401/404/503 | 已认证 / ApiKey |
| `/api/chat/messages/{id}/feedback` | POST | 点赞/踩 `{feedback:1\|-1, reason?}` | 400/401/404 | 所有者 |

**问答请求**：

```json
{ "sessionId": 101, "content": "分布式事务有哪些模式？",
  "kbIds": [1, 2], "suggestions": true }
```

**SSE 事件协议**见 §5。

### 2.5 用量统计（F2.5）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/analytics/daily` | GET | 问答量（日/周/月，`range` 参数） | 400/401 | OWNER+/ADMIN |
| `/api/analytics/tokens` | GET | Token 消耗与费用（按模型） | 400/401 | OWNER+/ADMIN |
| `/api/analytics/top-documents` | GET | 热门文档 Top10 | 400/401 | OWNER+/ADMIN |
| `/api/analytics/dau` | GET | 活跃用户趋势 | 400/401 | OWNER+/ADMIN |
| `/api/analytics/health` | GET | 知识库健康度（无答案率/低置信率） | 400/401 | OWNER+/ADMIN |
| `/api/analytics/export` | GET | 导出 CSV/Excel（`type` 参数） | 400/401 | OWNER+/ADMIN |

### 2.6 管理后台（F2.6）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/admin/users` | GET | 用户列表 | 400/401/403 | ADMIN |
| `/api/admin/users` | POST | 创建用户（可带 orgId） | 400/401/403 | ADMIN |
| `/api/admin/users/{id}` | PATCH | 启用/禁用/改角色/改部门 | 401/403/404 | ADMIN |
| `/api/admin/audit-logs` | GET | 审计日志（分页） | 400/401/403 | ADMIN |

### 2.7 全文搜索（F2.9）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/search` | GET | 全文搜索 `keyword&kbIds&types&dateFrom&dateTo&page&size` | 400/401 | 已认证 / ApiKey |

**响应示例**：

```json
{ "items": [{ "documentId": 12, "fileName": "分布式事务规范.pdf", "kbId": 1,
              "pageNo": 3, "sectionTitle": "Seata", "fileExt": "pdf",
              "snippet": "...<mark>分布式事务</mark>有 Seata AT 与 TCC...",
              "score": 18.2, "updatedAt": "2026-08-01T09:00:00Z" }],
  "total": 32, "page": 1, "size": 20, "hasMore": true }
```

### 2.8 文档在线预览（F2.10）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `GET /api/documents/{id}/content` | GET | 原始文件流（支持 Range，`page=` 定位） | 401/403/404/409 | view 权限 |
| `GET /api/documents/{id}/preview-url` | GET | 前端渲染定位 URL | 401/403/404 | view 权限 |

- 预览权限：ACL `view`（E-2103）；未发布文档返回 E-2102（审核/管理角色除外）。

### 2.9 组织架构（F2.12）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/admin/orgs` | GET | 部门树 | 401/403 | ADMIN |
| `/api/admin/orgs` | POST | 创建部门 `{parentId,name}` | 400/401/403/409 | ADMIN |
| `/api/admin/orgs/{id}` | PATCH | 改名/排序 | 401/403/404 | ADMIN |
| `/api/admin/orgs/{id}` | DELETE | 删除（须无成员/子部门） | 401/403/404/409 | ADMIN |
| `GET /api/auth/sso/providers` | GET | SSO 可用性（配置占位） | 200 | 公开 |

### 2.10 内容审核（F2.13）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `/api/admin/reviews` | GET | 待审队列（分页，status=PENDING_REVIEW） | 400/401/403 | ADMIN/REVIEWER/OWNER |
| `/api/admin/reviews/{documentId}/approve` | POST | 审核通过 | 401/403/404/409(E-2201) | ADMIN/REVIEWER/OWNER |
| `/api/admin/reviews/{documentId}/reject` | POST | 驳回 `{comment}` | 400/401/403/404/409(E-2201) | ADMIN/REVIEWER/OWNER |
| `/api/documents/{id}/reviews` | GET | 审核历史 | 401/403/404 | 成员/审核人 |

### 2.11 文档级 ACL（F2.14）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `GET /api/documents/{id}/acl` | GET | ACL 列表 | 401/403/404 | OWNER |
| `POST /api/documents/{id}/acl` | POST | 添加 `{principalType,principalId,permission}` | 400/401/403/404 | OWNER |
| `DELETE /api/documents/{id}/acl/{aclId}` | DELETE | 移除 | 401/403/404 | OWNER |

### 2.12 标签与收藏（F2.15）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `GET /api/tags` | GET | 标签列表（租户） | 401 | 已认证 |
| `POST /api/documents/{id}/tags` | POST | 打标签 `{name}`（不存在则创建） | 400/401/403/404 | EDITOR+ |
| `DELETE /api/documents/{id}/tags/{tagId}` | DELETE | 移除标签 | 401/403/404 | EDITOR+ |
| `GET /api/favorites` | GET | 我的收藏（分页） | 401 | 已认证 |
| `POST /api/favorites/{documentId}` | POST | 收藏 | 401/404 | 已认证 |
| `DELETE /api/favorites/{documentId}` | DELETE | 取消收藏 | 401/404 | 已认证 |

### 2.13 Webhook 集成（F2.15 低优先级）

| 端点 | 方法 | 说明 | 主要失败 | 权限 |
|------|------|------|----------|------|
| `GET /api/admin/webhooks` | GET | 订阅列表 | 401/403 | ADMIN |
| `POST /api/admin/webhooks` | POST | 创建订阅 `{name,url,events,secret?}` | 400/401/403 | ADMIN |
| `DELETE /api/admin/webhooks/{id}` | DELETE | 删除订阅 | 401/403/404 | ADMIN |

---

## 3. Java ↔ Python 内部端点（F2.2/F2.3/F2.4）

> 仅 server 可调用（集群内网），`rag-engine` 不做用户级鉴权（信任内网）。完整定义：`api/rag-engine.openapi.yaml`。

| 端点 | 方法 | 请求 | 成功响应 | 主要失败 |
|------|------|------|----------|----------|
| `/api/ingest/documents` | POST | `{documentId, objectKey, kbConfig{embeddingModel,chunkSize,chunkOverlap}, tenantId, versionNo}` | `202 {taskId}` | 400/422/503 |
| `/api/ingest/tasks/{id}` | GET | — | `IngestTaskStatus{stage,status,vectorCount,errorMsg}` | 404 |
| `/api/ingest/delete` | POST | `{documentId, versionNo?}` | `200 {deletedCount}` | 400 |
| `/api/query/chat` | POST | `{requestId, sessionId, kbIds, question, history[], kbConfig}` | `text/event-stream` | 400/422/503 |
| `/api/query/rerank` | POST | `{requestId, query, candidates[{chunkId,text}], topN}` | `200 {items:[{chunkId,score}]}` | 400/422 |
| `/api/query/search` | POST | `{requestId, keyword, kbIds, docIdWhitelist, types, dateFrom, dateTo, page, size, vectorFusion}` | `200 SearchResultPage` | 400/422 |
| `/api/engine/health` | GET | — | `200 {status, models:[{name,available}]}` | 503 |
| `/api/engine/route-status` | POST | `{routeType, modelName}` | `200 {available, latencyMs}` | 400 |

**SSE 事件对象**（`/api/query/chat`）与 §5 一致，`sources` 由 rag-engine 在 `meta`/`final` 事件携带。

---

## 4. OAuth2 端点与 Token 模型

### 4.1 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/oauth2/token` | POST | password grant：`grant_type=password&username=&password=`（form） |
| `/oauth2/token/refresh` | POST | `refreshToken` 轮换 |
| `/oauth2/token/revoke` | POST | 吊销 |

### 4.2 JWT 声明约定

```json
{ "sub": "userId", "tenant_id": 1, "roles": ["USER"], "iss": "ragkb",
  "iat": ..., "exp": ..., "jti": "..." }
```

- `tenant_id`、`roles` 由 `JwtAuthenticationConverter`（`03§2.1.1`）提取。
- RS256 签名；JWK Set 由本服务 `/jwks` 端点或认证中心分发。

### 4.3 安全约束

- accessToken TTL 2h；refreshToken 7d 一次性轮换；登出进 Redis 黑名单。
- 不将 token 写入 localStorage 明文（前端用内存 + 刷新拦截器，见 `01§4.6` 验收）。

---

## 5. SSE 事件协议（问答流式）

> `POST /api/chat/messages` 响应 `Content-Type: text/event-stream`，事件命名与顺序约定如下。

**事件命名与顺序**：`meta` → `token`* → `final`，或 `meta` → `error`。

| 事件 | 字段 | 说明 |
|------|------|------|
| `meta` | `{sessionId, messageId, kbIds, modelName, answerStatus}` | 首事件，含本次模型路由结果 |
| `token` | `{seq, text}` | 逐 token |
| `sources` | `{items:[{documentId,fileName,pageNo,sectionTitle,chunkId,score}]}` | 引用（可在 final 前单独发） |
| `final` | `{messageId, answerStatus, confidence, sources, suggestions, tokenIn, tokenOut, cost}` | 结束事件 |
| `error` | `{code, message, requestId}` | 错误终止（E-xxxx） |

**通用约定**：

```json
event: token
data: {"seq": 3, "text": "分布式"}

event: final
data: {"messageId": 501, "answerStatus": "answered", "confidence": 0.82,
       "sources": [{"documentId": 12, "fileName": "分布式事务规范.pdf", "pageNo": 3,
                    "sectionTitle": "Seata", "chunkId": "c_9f3b...", "score": 0.91}],
       "suggestions": ["Seata AT 与 TCC 的取舍？", "..."], "tokenIn": 320, "tokenOut": 180, "cost": 0.012}
```

- 每个 `data` 为一行 JSON；`id` 字段 = `seq`（递增）。
- **取消语义**：客户端断开或发 `AbortController` → 服务端停止生成（取消 LLM 流，已产 token 不落库为 assistant 完整消息）。
- `answerStatus`：`answered | no_answer | low_conf`（权威判定见 `03§3.2`）。

---

## 6. 文件上传 / 分片协议

| 步骤 | 端点 | 请求 | 响应 |
|------|------|------|------|
| 1. init | `POST /api/documents/upload/init` | `{fileName, fileSize, kbId, md5, partSize=5MB}` | `{uploadId, alreadyUploadedParts[], uploadComplete(bool=秒传)}` |
| 2. parts | `PUT /api/documents/upload/{uploadId}/parts/{partNumber}` | multipart `file` | `{partNumber}` |
| 3. complete | `POST /api/documents/upload/{uploadId}/complete` | — | `{documentId, status}` |

- 秒传：md5 指纹命中已存在文件 → 直接 complete。
- 断点续传：init 返回 `alreadyUploadedParts`，前端补传缺失分片。
- 合并幂等：同一 uploadId 多次 complete 只触发一次解析（`idem:parse` 幂等键）。
- ZIP 批量导入：`POST /api/kb/{kbId}/documents/batch-zip`（multipart `zip`），返回 `{batchId}`，进度通过文档列表状态追踪。

---

## 7. 错误码字典

> 与 `03§9` 全表一致，前端按 `code` 差异化处理。典型处理：

| code | 前端行为 |
|------|----------|
| E-1002/E-6001 | 清除 token，跳转登录 |
| E-1003 | 提示无权限，刷新成员角色 |
| E-1005/E-6003 | 提示限流，禁用按钮 N 秒 |
| E-2001 | 提示格式不支持，过滤文件类型 |
| E-2004 | 显示解析失败原因 + 重试按钮 |
| E-2101 | 提示"扫描件识别失败"，可重试 |
| E-2102 | 提示"文档待审核/未发布" |
| E-2103 | 提示"无权查看该文档" |
| E-2201/E-2202 | 提示"文档不在待审核状态"，刷新审核队列 |
| E-3002 | 提示"知识库暂无可用文档" |
| E-1201 | 提示"SSO 未启用" |

---

## 8. 契约版本与变更管理

1. 改契约先改 OpenAPI YAML（`api/*.openapi.yaml`），再同步本文摘要表。
2. 不兼容变更（字段删除/类型变更/路径变更）必须升版本并登记到本文头部。
3. 变更必须同步：`03` 对应接口签名、`08` 契约测试、`06` 网络边界。
4. 每个端点的字段枚举（如 `status`、`answerStatus`）以本契约与 `03` 状态机为准，前端不硬编码。
