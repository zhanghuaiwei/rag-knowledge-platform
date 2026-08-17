# ragkb-web — 前端 / BFF

> 通用企业知识库平台的 **Next.js 前端 / BFF**:管理、搜索、问答、治理与质量体验。产品化页面已按 **antd v5 + ECharts + G6 血缘** 落地,统一走 **真实 HTTP transport**(与 `service` 接口骨架对齐)。

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

前端内置一套与 v0.2 数据库设计(`../deploy/ddl/init.sql`)和 API 契约(`../docs/07-API契约.md`)对齐的**完整 mock 数据**,覆盖:

- 知识库(KB)与成员角色
- 文档、版本与解析状态
- 搜索/问答、SSE 流式会话与引用
- 用量统计、成本、Top 文档、DAU
- 用户、组织、审计日志、审核队列、标签、收藏
- API Key、Webhook、连接器

### 环境配置与 Mock 切换

Next.js 根据命令自动加载环境文件：`pnpm dev` 使用 `.env.development`，生产构建使用
`.env.production`，`.env.local` 可作为本机最高优先级覆盖。仓库提供不含密钥的模板：

```bash
# 本地开发（仓库已提供一份被 Git 忽略的 .env.development）
cp .env.development.example .env.development
pnpm dev

# 生产构建前复制并替换 example 域名
cp .env.production.example .env.production
pnpm build
```

| 变量 | 用途 | 开发默认值 |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | 全局后端地址（不含 API 前缀） | `http://localhost:8080` |
| `NEXT_PUBLIC_API_PREFIX` | 全局 API 前缀 | `/api/v1` |
| `NEXT_PUBLIC_MINIO_BASE_URL` | 浏览器可访问的 MinIO/对象存储地址 | `http://localhost:9000` |

配置统一从 `config/env.ts` 读取。修改 `NEXT_PUBLIC_*` 后需要重启开发服务；这些变量会进入
浏览器 bundle，禁止填写密码、token、MinIO access key/secret key 等秘密信息。

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
- [x] 产品化页面(mock 演示):工作台、问答(模拟流式)、搜索、知识库(列表/向导/详情)、文档库/详情、治理审核、质量用量、管理中心(成员/API Key/Webhook/审计)、登录页;设计见 `../docs/modules/enterprise-generalization/design/web-product-design.md`
- [x] 主题与布局可配置(明暗、主题色、圆角、密度、内容宽度、侧边栏收起,localStorage 持久化)
- [x] UI 全面迁移 antd v5 组件库(布局/菜单/表格/表单/弹窗/消息)并接入 G6 血缘可视化(`components/lineage-graph.tsx`)、ECharts 图表(趋势/环形/仪表盘)
- [x] 企业级产品化补齐:写操作真实落地 mock 库(创建/克隆/成员/审核/收藏/删除/重试/回滚/API Key/Webhook/组织/标签)、角色门禁(管理员/治理角色路由守卫 + 导航过滤)、治理中心(元数据 schema/保留与法律保全/删除与证明)、文档 ACL 编辑器(可解释权限)、组织树 CRUD、问答知识库多选、搜索类型/日期/排序、MD/TXT 预览、用量日/周/月与 CSV 导出
- [x] 真实 HTTP transport(axios):全部功能点 API 对接(`api-client/http/`),含信封解壳、统一错误归一化、异步任务轮询、问答 SSE 流式消费;固定走真实 HTTP,`NEXT_PUBLIC_API_BASE_URL` 指定后端地址
- [ ] OIDC Code + PKCE 登录、BFF 会话与 CSRF 防护(当前登录走真实 HTTP,后端 form 登录端点就绪但实现待人工补齐;实现点见 `service/application` 桩)
- [ ] Playwright E2E 与可访问性基线验证
