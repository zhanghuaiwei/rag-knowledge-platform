# AGENTS.md

本文件是 AI 在 `rag-knowledge-platform` 仓库工作的轻量入口。详细规则按需从 `engineering/rules/` 加载。

## 开始工作前

1. 读取 `.ai/project.json`，确认项目目录、模块和验证命令。
2. 读取 `.ai/current-role.md`；如果不存在，参考 `.ai/current-role.example.md` 并询问或生成当前角色。
3. 查看 `git status --short --branch`，保留用户已有改动，不覆盖或回滚无关文件。
4. 读取 `engineering/rules/project.md`、当前角色规则和当前任务清单；不要一次加载全部规则。
5. 新功能先确认需求和唯一 API 契约，再分别实现后端与前端。

## 规则加载顺序

1. `engineering/rules/project.md`
2. `engineering/rules/roles/<role>.md`
3. `engineering/rules/checklists/<task>.md`
4. 必要时再读取更具体的模块文档和现有代码

冲突时，以更具体、离当前实现更近且经过确认的契约和代码约定为准；发现入口文档过期时同步提出修正。

## 项目入口

- 后端：`service/`
- 前端：`web/`
- 模块文档：`docs/modules/<module>/`
- AI 使用说明：`docs/ai/README.md`

## 项目内 Skill

标准版在 `engineering/skills/` 提供需求澄清、契约优先、前端和后端工作流。任务与某个 Skill 的描述匹配时，先完整读取对应 `SKILL.md`，再按其中流程执行；Skill 只负责编排，正式约束仍以 `engineering/rules/` 和模块契约为准。

## 开发红线

- 未获当前角色授权，不修改对应业务代码、公共规则、部署配置或真实数据。
- 不把业务逻辑堆在 HTTP/Controller 层，前端页面不直接拼接完整请求地址。
- 字段、枚举、分页、错误码、权限和危险操作必须以同一份契约为准。
- SQL、权限、部署、外部消息和真实数据操作先预览并说明影响，真实执行前获得确认。
- 不提交密钥、令牌、账号、私网地址或本地环境文件。
- 不把 mock、演示数据或候选扫描结果描述为真实验收证据。

## 验证与交付

- 优先运行与改动范围匹配的最小构建、类型检查、静态检查和测试。
- 未执行的检查要说明原因、风险和建议命令。
- 交付说明包含变更文件、验证结果、遗留风险，以及数据库、配置、权限和部署影响。
- 提交信息使用 `<type>: <description>`，常用 type：`feat`、`fix`、`docs`、`refactor`、`test`、`chore`。
