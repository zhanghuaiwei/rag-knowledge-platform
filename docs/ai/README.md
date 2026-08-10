# rag-knowledge-platform AI 开发说明

## 第一次使用

1. 编辑 `.ai/project.json`，补充真实目录、模块和构建/测试命令。
2. 将 `.ai/current-role.example.md` 复制为 `.ai/current-role.md`，填写当前角色并在项目现有忽略规则中排除该本地文件。
3. 让 AI 从 `AGENTS.md` 开始，按角色和任务逐层加载规则。

## 文档目录

每个模块放在 `docs/modules/<module>/`：

- `requirements/`：目标、范围、业务规则和验收标准
- `design/`：流程、模型、交互和技术设计
- `api/`：唯一 API 契约及版本
- `tasks/frontend/`、`tasks/backend/`：可执行任务
- `test/`：测试计划、用例和结果
- `ops/`：发布、配置、监控和回滚
- `support/`：问题记录、复现、归因和复盘

## MCP

脚手架默认不生成 MCP 配置。只有初始化时显式传入 `--mcp-url` 才生成 `.mcp.json` 和 `.codex/config.toml`。不要把带令牌的地址、私网凭据或个人配置提交到公共仓库。
