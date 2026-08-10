# api-client

前端 API 层。规则：**组件中禁止直接 fetch**；请求地址与协议细节只在本目录出现。

## 结构

```text
api-client/
├── index.ts        # 统一出口：api + 类型
├── client.ts       # ApiClient 接口 + 按环境变量选择 transport
├── types.ts        # 契约类型（对齐 OpenAPI / 07-API契约）
├── mock/           # mock transport：从 mocks/db 读取数据并模拟延迟
└── http/           # 真实 HTTP transport（OpenAPI v0.2 冻结后实现，当前为占位）
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
| `NEXT_PUBLIC_USE_MOCK=false` | 使用真实 HTTP transport（OpenAPI v0.2 冻结后接入） |

OpenAPI v0.2 冻结后：由契约生成 client/type，替换 `mock/` 与 `http/` 实现；
页面代码只依赖 `ApiClient` 接口，无需改动。
