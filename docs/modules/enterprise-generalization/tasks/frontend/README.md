# 企业通用化前端任务

> **状态**：待评审 · **版本**：v0.2-draft · **负责人**：待指定 · **最近更新**：2026-08-10
> **需求**：[`../../requirements/README.md`](../../requirements/README.md) · **设计**：[`../../design/README.md`](../../design/README.md)

## 前置门禁

- [x] web 工程建立，配置 lint/typecheck/test 命令（`pnpm lint / typecheck / test`；E2E 待补）。
- [x] API client 与类型统一在 `api-client/`（mock + http 双 transport），页面不直接拼接完整请求地址；类型为手写契约（`api-client/types/`），非 OpenAPI 生成。
- [ ] OIDC/BFF 形态、路由权限和 OpenAPI 版本已确认（OIDC 登录未落地，见 Gate 0）。

## Gate 0：安全与契约

- [ ] 替换用户名密码 token 表单：采用 OIDC Code + PKCE 登录/回调/登出（当前登录页为 dev 账号表单 + mock SSO 入口，未接 OIDC）。
- [ ] token 不落 localStorage；若采用 BFF，使用 Secure/HttpOnly/SameSite cookie 并实现 CSRF 防护（当前为 `ragkb-auth` mock 会话，localStorage；真实后端 HttpOnly 会话待 service 业务用例接通）。
- [x] 路由/导航角色门禁已实现（`components/route-guard.tsx` + `nav-config.tsx`：管理员/治理角色守卫、导航过滤）；服务端统一 401/403/404 处理与租户切换缓存清理待真实后端落地。
- [ ] 从 OpenAPI 生成 client/type/error map；加入契约版本检查（当前类型为手写，未生成、无版本检查）。
- [x] 预览与下载分级 UI 已实现：无 `download_original` 不渲染下载入口、不请求原始流（`document/preview-dialog.tsx`）；历史引用重授权流程待真实数据链路。
- [x] SSE 已实现 meta/token/sources/final/error 聚合、断连/取消/重复事件处理与事件去重（`api-client/http/sse.ts`，有单测）；模型输出按净化后渲染（`lib/markdown.ts`）。

## 企业 MVP 体验

- [x] 知识库创建向导（4 步：基本信息 → 归属与治理 → 策略与配额 → 确认），见 `app/(main)/kbs/new/`。
- [x] 文档列表状态列：来源、摄取阶段、审核、敏感级、所有者、版本、失败可定位（错误码 + 重试入口），见 `app/(main)/documents/`。
- [ ] 元数据表单由租户 schema 驱动 + 批量编辑预览（schema 列表/发布已实现；schema 驱动表单编辑为 stub 待办）。
- [x] 权限编辑器可解释继承来源与最终有效权限（`components/document/acl-editor.tsx`，USER/ORG/ROLE/KB_ROLE + 三档权限点）。
- [x] 问答展示来源版本/页/段、置信/拒答状态、内容可能过期提示；反馈（👍/👎 + 结构化原因），见 `app/(main)/chat/`。
- [x] 管理页 API Key：scope/允许 KB/期限、最近使用、吊销；明文只展示一次并需复制确认，见 `app/(main)/admin/api-keys/`。

## 通用化扩展体验

- [ ] 连接器中心按能力展示授权、范围、同步模式、健康状态（列表页已实现）；OAuth 配置流程、源 ACL 映射、真实同步为 stub 待办。
- [ ] 同步任务展示游标/新增/更新/删除/失败数、限流状态、单对象重放（页面入口已占位，真实数据待 service 业务用例）。
- [x] 治理中心：元数据 schema（发布）、保留策略、法律保全（创建/解除）、复审队列（批量通过/驳回）、删除审批与删除证明查询，见 `app/(main)/governance/`。
- [x] 质量与用量面板：日/周/月聚合、问答/无答案/DAU/成本、Top 文档、配额进度、CSV 导出，见 `app/(main)/analytics/` 与 `app/screen/`。
- [x] 危险操作三段式（预览影响 → 确认 → task 进度）：批量审核/删除/撤销/换模/重建索引均有确认与任务中心反馈（`components/task-center.tsx`，轮询 mock/http task）。

## 完成定义

- [ ] loading/empty/error/disabled/stale/partial-success/offline 状态完整（`components/async-state.tsx` 已提供骨架屏/空态/错误态；剩余状态按页面补齐）。
- [ ] 组件级测试覆盖权限显示、错误映射、schema 表单和 SSE（已有 `app/page`、`http/sse`、`mock`、`format` 等 Vitest 用例）；Playwright 覆盖关键路径**未完成**。
- [ ] 可访问性达到 WCAG 2.2 AA 基线（未系统验证）。
- [x] 敏感数据不进入前端日志/埋点/URL（`observability/` 文档约定；截图与测试数据脱敏规范见 §7）。
- [x] typecheck、lint、test 有真实执行记录（`pnpm lint / typecheck / test`）；E2E 与 UI 截图验收待补。

