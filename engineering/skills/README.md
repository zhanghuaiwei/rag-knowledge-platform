# rag-knowledge-platform 项目 Skill

本目录维护项目内 AI Skill 的薄封装。Skill 用来路由高频流程，不复制正式规范，也不替代 `engineering/rules/`、模块 API 契约和当前代码。

| Skill | 用途 |
| --- | --- |
| `feature-brainstorm` | 需求澄清、方案比较、MVP 和待确认问题 |
| `contract-first` | 需求、API、前后端任务和测试的一致性检查 |
| `backend-coding-standards` | 后端实现、审查和聚焦验证流程 |
| `frontend-coding-standards` | 前端实现、交互质量和聚焦验证流程 |

使用 Skill 前先读取 `.ai/current-role.md`、`engineering/rules/project.md` 和匹配的角色/任务规则。版本和维护状态见 `skill-registry.json`。
