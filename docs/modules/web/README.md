# Web 前端模块（ragkb-web）

## 模块定位

`web/` 是平台的 Next.js 15（App Router）前端：登录后主框架（工作台 / 问答 / 搜索 /
知识库 / 文档库 / 治理 / 质量用量 / 管理中心）、运营数据大屏与登录页。全部数据与写操作
经 `api-client` 走真实 HTTP transport 对接 `service` 后端，**没有 mock 数据层与切换开关**。

## 架构与红线

- **api-client 三层结构**（页面唯一数据入口，组件禁止直接 `fetch`、禁止拼接完整请求地址）：
  - `api-client/types/`：契约类型（按域拆分，枚举值以服务端契约为准）；
  - `api-client/contracts/`：各域接口形状（与 OpenAPI 对齐）；
  - `api-client/http/`：唯一真实 HTTP transport（axios；`client.ts` 固定导出 `httpClient`）。
- **统一信封与错误**：后端 `{ code, message, data }` 信封解壳；401 单飞刷新
  （`POST /auth/refresh`，HttpOnly cookie）后重试一次；错误归一化为 `ApiError`。
- **异步任务模式**：上传 / 克隆 / 连接器同步等 202 + Task 接口，由前端轮询
  （`waitForTask` → `GET /tasks/{id}`，或连接器同步的 `GET /sync-jobs/{jobId}`）到终态后回读资源。
- **多租户**：租户身份由 JWT 承载（Bearer + 刷新 cookie），任何请求体不携带租户字段。

## 功能-接口映射表

前缀 `/api/v1`（由 `NEXT_PUBLIC_API_BASE_URL` + `NEXT_PUBLIC_API_PREFIX` 组合）。

### 认证与会话（`app/login`、`app/(main)/change-password`）

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 登录 | `POST /auth/login` | form 登录，返回 accessToken（内存）+ 刷新 cookie |
| 会话回读 | `GET /auth/session` | 刷新页面后恢复登录态 |
| 刷新令牌 | `POST /auth/refresh` | 401 单飞刷新，`withCredentials` |
| 租户切换 | `POST /auth/tenant/switch` | 多租户成员切换当前租户 |
| 登出 | `POST /auth/logout` | 清理会话 |
| 修改密码 | `POST /auth/change-password` | 登录后自助改密 |

### 知识库（`app/(main)/kbs`）

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 知识库列表 / 详情 | `GET /kbs`、`GET /kbs/{id}` | 分页与角色信息 |
| 创建知识库 | `POST /kbs` | 请求体不含租户字段（JWT 承载归属） |
| 更新设置 | `PATCH /kbs/{id}` | 名称 / 描述 / 发布前审核 |
| 克隆 | `POST /kbs/{id}/clone` | 202 + Task，轮询终态后按 resourceId 回读新库 |
| 归档 / 删除 | `POST /kbs/{id}/archive`、`DELETE /kbs/{id}` | 危险操作，前端二次确认 |
| 成员管理 | `GET/POST /kbs/{id}/members`、`DELETE /kbs/{id}/members/{userId}` | 邀请 / 角色变更 / 移除（OWNER） |
| 连接器列表 | `GET /connections` | 详情页连接器 Tab |
| 触发手动同步 | `POST /connections/{id}/sync` | body `{ syncType: "INCREMENTAL" }`；202 + Task（resourceType=SYNC_JOB） |
| 同步任务详情 | `GET /sync-jobs/{jobId}` | 受理后按 Task.resourceId 轮询至终态（QUEUED/RUNNING/SUCCEEDED/PARTIAL/FAILED/CANCELLED） |

### 文档（`app/(main)/documents`）

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 文档列表 / 详情 | `GET /documents`、`GET /kbs/{kbId}/documents`、`GET /documents/{id}` | 状态 / 敏感级 / 关键词筛选 |
| 分片上传 | `POST /upload/init` → `PUT /upload/{id}/parts/{n}` → `POST /upload/{id}/complete` | 按服务端 partSize 切片直传；complete 后轮询任务终态 |
| 元数据更新 | `PATCH /documents/{id}` | 标题 / 敏感级 / 标签 |
| 删除 / 重试解析 / 回滚 | `POST /documents/{id}/deletion`、`/reparse`、`/rollback` | 均为异步或受控操作 |
| 版本历史 | `GET /documents/{id}/versions` | |
| ACL | `GET/PUT /documents/{id}/acl` | 可解释权限编辑 |
| 预览 / 下载 | `GET /documents/{id}/preview`、`GET /documents/{id}/download` | 字节流 → Blob URL；403 提示无 VIEW_CONTENT 权限 |
| 收藏 | `POST/DELETE /favorites`、`GET /favorites` | |

### 问答与搜索（`app/(main)/chat`、`app/(main)/search`）

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 会话列表 / 新建 | `GET/POST /chats` | |
| 流式问答 | SSE（`api-client/http/sse.ts`） | 流式消费引用与置信度 |
| 搜索 | `POST /search` / `GET /search` | 类型 / 日期 / 排序 |

### 治理与审核（`app/(main)/governance`）

| 功能 | 接口 |
| --- | --- |
| 审核队列 | `GET /reviews`（及审核动作端点，见 `api-client/http/review.ts`） |
| 元数据 schema | `GET/POST /metadata-schemas` |
| 保留策略 / 法律保全 | `GET/POST /retention-policies`、`GET/POST /legal-holds` |
| 删除任务与证明 | `GET /deletion-tasks`、`GET /deletion-receipts` |
| 标签 | `GET/POST /tags` |

### 分析与大屏（`app/(main)/analytics`、`app/screen`）

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 用量明细 | `GET /analytics/usage?period=DAY/WEEK/MONTH` | 14 天 / 12 周 / 12 月窗口 |
| Token 成本 | `GET /analytics/costs` | 按模型聚合 |
| 热门文档 / DAU / 健康度 | `GET /analytics/top-documents`、`/dau`、`/kb-health` | 大屏同口径复用 |
| CSV 导出 | 前端 `lib/csv.ts` 本地生成 | 仅用量明细；后端 `GET /analytics/export`（usage/costs/top-documents/dau）暂未被前端消费 |

### 管理中心（`app/(main)/admin`）

| 功能 | 接口 |
| --- | --- |
| 用户 / 组织 | `GET/POST /users`、`GET/POST /orgs`（及角色 / 状态端点，见 `api-client/http/admin.ts`） |
| 审计日志 | `GET /audit-logs`（服务端过滤 + 分页） |
| API Key | `GET/POST /api-keys`（及启停 / 撤销端点） |
| Webhook | `GET/POST /webhook-subscriptions`（及启停 / 撤销端点） |

### 任务与通知（全局组件 `components/task-center.tsx`、`notification-center.tsx`）

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 任务列表 / 取消 | `GET /tasks`、`POST /tasks/{id}` | 运行中每 3 秒轮询 |
| 任务轮询 | `GET /tasks/{id}` | `waitForTask` 通用工具（8s 超时） |
| 通知 | `GET /notifications`、`POST /notifications/{id}/read`、`POST /notifications/read-all` | |

## 本次 mock 清理清单（2026-08-17）

| # | 位置 | 处理 | 理由 |
| --- | --- | --- | --- |
| 1 | `app/(main)/kbs/new/page.tsx` | 删除创建请求体中的 `tenant_id: "1"`；同步删除 `types/kb.ts` 中 `CreateKbInput.tenant_id` 契约字段 | 租户身份由 JWT 承载，请求体不应携带（且为硬编码 snake_case） |
| 2 | `app/(main)/analytics/page.tsx` | 配额常量改为 `QUOTA_PLACEHOLDERS`，卡片标题标注「暂未接入」，底部说明数值仅为占位 | 后端暂无租户配额接口；保留 UI、影响最小，等待 GKB-08 契约。`lib/csv.ts` 导出不含配额字段，无需改动 |
| 3 | `app/(main)/admin/audit/page.tsx` | 导出按钮改为明确的契约缺口提示（不再显示 mock 字样） | 后端 `GET /analytics/export` 白名单（usage/costs/top-documents/dau）无 audit 类别；审计导出需权限校验与脱敏契约冻结 |
| 4 | `app/(main)/kbs/[id]/page.tsx` + `api-client` 三层 | 「立即同步」「任务详情」接通 `POST /connections/{id}/sync` 与 `GET /sync-jobs/{jobId}`；新增 `syncConnector` / `getSyncJob`（contracts → http → types） | 同步受理后轮询任务至终态并 toast 结果；任务详情弹窗展示状态 / 发现数 / 失败对象 |
| 5 | `app/screen/page.tsx` | 清理 KPI 标签「（mock）」、页脚「当前为 mock 演示数据」与文件头注释 | 大屏数据已全部来自真实接口 |
| 6 | `components/document/preview-dialog.tsx` | `mockPreviewBody` 更名 `placeholderBody`，注释改为准确现状 | 仅在文本流读取失败回退与二进制格式（docx 等，待服务端转码）占位两个场景渲染 |
| 7 | `components/upload-document-modal.tsx` 等 4 个组件 | 过期「mock」注释改为真实描述（上传 = 真实分片上传；设置 / 成员 = 真实落库；任务取消 = 真实生效） | 避免误导后续维护者 |
| 8 | `web/README.md` | 移除 `mocks/` 目录与 mock transport 开关描述，更新目录结构与状态清单 | 与当前真实架构一致 |
| 9 | `app/api/health/route.ts` | `phase` 由 `scaffold` 改为 `mvp`（测试断言同步） | 反映当前阶段 |

## 遗留前端缺口

| 缺口 | 位置 | 现状 | 前置条件 |
| --- | --- | --- | --- |
| 租户配额 | `app/(main)/analytics/page.tsx` | 本地占位常量 + 「暂未接入」标注 | 等待租户配额查询接口（GKB-08 契约冻结） |
| 审计导出 | `app/(main)/admin/audit/page.tsx` | 按钮保留，点击提示契约缺口 | 需审计导出契约（权限校验 + 脱敏）；后端 `/analytics/export` 需新增 audit kind |
| 文档血缘图 | `app/(main)/documents/[id]/page.tsx`、`components/lineage-graph.tsx` | 由文档详情推导的**示意数据**（来源→文档→分块→索引→消费），非真实血缘 | 等待血缘查询接口 |
| 二进制格式预览转码 | `components/document/preview-dialog.tsx` | PDF / 图片 / 文本走真实文件流；docx / pptx / xlsx 展示占位提示 | 需服务端转码（或前端引入 PDF.js / mammoth / SheetJS 渲染链） |
| 文档详情页局部示意字段 | `app/(main)/documents/[id]/page.tsx` | 语言识别、复审日期、下载权限推导、摄取失败错误码为示意值 | 等待对应契约字段 / 策略结果接口 |
| 连接器「任务详情」数据范围 | `app/(main)/kbs/[id]/page.tsx` | 仅展示本会话最近一次触发的同步任务（连接器列表接口无最近 jobId 字段） | 连接器契约增加最近任务 id 后可恢复历史查询 |

## 验证

```bash
cd web
pnpm typecheck   # tsc --noEmit
pnpm lint        # eslint
pnpm test        # vitest run
```
