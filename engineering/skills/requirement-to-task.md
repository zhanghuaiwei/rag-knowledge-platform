# 需求到规则与 Skill 路由（rag-knowledge-platform）

## 推荐顺序

```text
需求澄清 -> 设计 -> 唯一 API 契约 -> 后端 -> 前端 -> 测试 -> 发布
```

| 用户意图 | 优先 Skill | 同时读取 |
| --- | --- | --- |
| 探索想法、梳理范围 | `feature-brainstorm` | 模块 requirements/design |
| 字段、枚举、分页、错误码对账 | `contract-first` | `checklists/contract.md` |
| 后端接口、服务、数据访问 | `backend-coding-standards` | `roles/backend.md`、`checklists/backend-api.md` |
| 前端页面、组件、API client | `frontend-coding-standards` | `roles/frontend.md`、`checklists/frontend-crud.md` |

涉及 SQL、权限、部署、真实数据和外部系统时，默认先预览影响和回滚方式，真实执行前确认。
