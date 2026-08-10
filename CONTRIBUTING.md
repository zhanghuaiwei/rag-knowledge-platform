# 贡献指南

欢迎向 `rag-knowledge-platform` 提交 Issue 与 PR。为保证代码质量与架构一致性,请遵循以下约定。

## 目录导航

- 项目入口与工程规则:`AGENTS.md`、`engineering/rules/project.md`
- 文档导航:`docs/README.md`
- AI 协作流程:`docs/frontend-backend-ai-collaboration.md`

## 分支模型

```text
main         <- 生产分支,只通过 PR 合入
develop      <- 开发主干
feature/*    <- 功能分支
fix/*        <- 修复分支
hotfix/*     <- 紧急修复
```

**禁止直接向 `main` / `master` 推送**,所有变更必须通过 PR 合入。

## 提交流程

1. 从 `develop` 切出功能分支:`git checkout -b feature/<short-name> develop`。
2. 小步提交,一次只改一件事;重构与功能修改不在同一 commit。
3. 提交信息使用 [Conventional Commits]:

   ```text
   <type>(<scope>): <subject>

   type: feat | fix | docs | style | refactor | perf | test | chore | ci
   scope: 模块名(如 service / rag-engine / web / docs)
   ```

4. AI 辅助的提交请按全局规则标注 `Assisted-by:` / `Generated-by:`。
5. 推送并创建 PR,描述包含:变更内容、变更原因、测试方式、影响范围。

## 代码规范

- 遵循仓库内 `engineering/rules/project.md` 与全局 CLAUDE.md(分层架构、命名、错误处理、性能红线)。
- 单文件不超过 300 行(配置/生成文件除外);函数单一职责、小于 50 行。
- 禁止 `console.log` 代替结构化日志;禁止空 catch;禁止 `@ts-ignore` / `as any`。
- 数据库操作一律参数化,禁止 `SELECT *` + 字符串拼接。

## 验证闭环

提交 PR 前必须通过最小验证(Inner Loop),未通过不得进入人工审查:

```bash
make lint        # 前端 lint + rag-engine ruff
make typecheck   # 前端类型检查
make test        # 前端 + rag-engine + service 测试
make security    # 轻量硬编码密钥扫描
```

CI 还会执行 OpenAPI 契约结构校验(见 `.github/workflows/ci.yml`)。

## 文档要求

- 公开 API 必须有 JSDoc / docstring;复杂逻辑注释"为什么"而非"是什么"。
- 新功能先确认需求与唯一 API 契约,再分别实现后端与前端;契约变更须同步检查前后端、测试、权限、数据和发布影响。
- 业务文档统一进入 `docs/modules/<module>/`;模板复制后及时替换占位内容。

## 审查与合入

- 一个 PR 只做一件事,修改文件不超过 20 个。
- 核心业务逻辑覆盖率 ≥ 80%,低于底线 CI 直接 fail。
- 审查通过后由维护者合入 `develop`;发布时再经 PR 合入 `main`。

## 开源许可

通过提交代码,你同意以 [MIT License](LICENSE) 授权你的贡献。

[Conventional Commits]: https://www.conventionalcommits.org/zh-hans/v1.0.0/
