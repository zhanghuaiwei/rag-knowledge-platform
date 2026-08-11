# api-client

前端 API 层。规则：**组件中禁止直接 fetch**；请求地址与协议细节只在本目录出现。

## 结构

```text
api-client/
├── index.ts        # 统一出口：api + 类型
├── client.ts       # ApiClient 接口 + 按环境变量选择 transport
├── contracts/      # 域契约接口（ApiClient = 10 域接口并集）
├── types/          # 契约类型（对齐前端产品契约 / OpenAPI）
├── mock/           # mock transport：从 mocks/db 读取数据并模拟延迟
└── http/           # 真实 HTTP transport（axios）
    ├── index.ts    # httpClient 组合
    ├── client.ts   # axios 实例 + 信封解壳 + 错误归一化 + 任务轮询
    ├── errors.ts   # ApiError（code/status/requestId）
    ├── sse.ts      # SSE 流式读取（问答流式）
    └── <domain>.ts # 各域真实 API 调用
```

## 使用

页面只从 `@/api-client` 导入：

```ts
import { api } from "@/api-client";

const page = await api.listKbs({ page: 1, size: 20 });
const detail = await api.getDocument(id);
```

## transport 切换

| 环境变量 | 行为 |
| --- | --- |
| `NEXT_PUBLIC_USE_MOCK` 未设置或 `true` | 使用内置 mock 数据（无需后端） |
| `NEXT_PUBLIC_USE_MOCK=false` | 使用真实 HTTP transport |

真实后端连接（开发）：

```bash
NEXT_PUBLIC_USE_MOCK=false NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 pnpm dev
```

`NEXT_PUBLIC_API_BASE_URL` 默认 `http://localhost:8080`，http transport 自动追加 `/api/v1`
前缀，并携带会话 cookie（`withCredentials`）。后端需开放 CORS 且 `allowCredentials`。

## 真实 HTTP transport 约定

- 后端统一返回 `{ code, message, data }` 信封，`code="0"` 为成功；非零抛 `ApiError`。
- 错误映射：HTTP 状态 + 后端 `code`/`message`/`requestId`（`ApiError`），页面 catch 后统一展示。
- 异步端点（克隆/删除/上传/重试等返回 `Task`）：transport 轮询 `GET /api/v1/tasks/{taskId}`
  至终态，再按 `resourceId` 回读实体，保持前端契约的同步返回语义。
- 问答提问为 SSE（meta → token* → sources? → final）：transport 内部消费并聚合成
  `ChatStreamResult`，前端契约不变（`http/sse.ts`）。
- 分页：后端 `PageData{items,total,page,size,hasMore}` 与前端 `PageResult` 同形；搜索为
  游标分页，transport 映射为 `PageResult`（首页）。
