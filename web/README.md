# ragkb-web — 前端 / BFF

> 通用企业知识库平台的 **Next.js 前端 / BFF**:管理、搜索、问答、治理与质量体验。当前为工程脚手架 + **完整 mock 数据层**,可脱离后端完整演示;业务页面在 OpenAPI v0.2 契约冻结后实现。

- 权威设计:`../docs/02-概要设计.md`、`../docs/03-详细设计.md`
- 契约:`../docs/api/server.openapi.yaml`
- 页面边界:`../docs/02-概要设计.md` 与通用化前端任务 `../docs/modules/enterprise-generalization/tasks/frontend/README.md`

## 技术栈

| 项 | 版本 |
| --- | --- |
| Next.js | 15(App Router) |
| React | 19 |
| TypeScript | 5 |
| 测试 | Vitest |
| 包管理 | pnpm |

## 目录结构

```text
web/
├── app/                     # App Router 页面与 API 路由
│   ├── api/health/          # 探针端点(脚手架阶段)
│   ├── layout.tsx
│   └── page.tsx
├── api-client/              # 前端 API 层(组件中禁止直接 fetch)
│   ├── types.ts             # 契约类型
│   ├── client.ts            # 客户端(自动选择 mock / http transport)
│   ├── http/                # 真实 HTTP transport(接入后端后使用)
│   └── mock/                # mock transport 实现
├── mocks/                   # 完整 mock 数据集与内存数据库
├── components/              # 跨 feature 复用组件(占位)
├── features/                # 业务功能模块(占位)
└── (next.config.ts / tsconfig.json / vitest.config.ts)
```

## Mock 数据

前端内置一套与 v0.2 数据库设计(`../deploy/ddl/01-schema.sql`)和 API 契约(`../docs/07-API契约.md`)对齐的**完整 mock 数据**,覆盖:

- 知识库(KB)与成员角色
- 文档、版本与解析状态
- 搜索/问答、SSE 流式会话与引用
- 用量统计、成本、Top 文档、DAU
- 用户、组织、审计日志、审核队列、标签、收藏
- API Key、Webhook、连接器

### 如何消费 mock

在 `api-client` 中按接口调用即可 —— 客户端会基于环境变量自动选择 transport:

```bash
# 使用 mock(默认,无需后端)
NEXT_PUBLIC_USE_MOCK=true pnpm dev

# 连接真实后端
NEXT_PUBLIC_USE_MOCK=false NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 pnpm dev
```

> 开发红线:组件中禁止直接 fetch;请求地址与协议细节只出现在 `api-client/` 目录。

## 快速开始

### 环境要求

- Node.js 22+ (Active LTS)、pnpm 9+。

### 安装与运行

```bash
cd web && pnpm install
pnpm dev        # 默认 http://localhost:3000
```

### 验证

```bash
pnpm lint        # eslint
pnpm typecheck   # tsc --noEmit
pnpm test        # vitest run
```

## 提交规范

- 新功能先确认需求与唯一 API 契约,再从 `api-client` 生成/手写类型与调用;页面不直接拼接完整请求地址。
- 提交信息遵循仓库 `CONTRIBUTING.md` 的 Conventional Commits;提交前通过 `make lint / typecheck / test`。

## 当前状态与后续

- [x] Next.js 工程、lint/typecheck/test 命令、`/api/health` 探针
- [x] 完整 mock 数据层(脱离后端可演示)
- [ ] OpenAPI v0.2 冻结后由契约生成 client/type(替换 mock transport)
- [ ] 按页面边界实现知识库 / 文档 / 问答 / 治理 / 用量页面
