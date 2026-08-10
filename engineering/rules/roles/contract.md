# 契约负责人规则

## 允许范围

- `docs/modules/*/api/`
- `docs/modules/*/design/`
- `docs/modules/*/test/`

## 需要确认

- `service/`
- `web/`
- `engineering/rules/`

## 工作方式

- 一个功能只维护一份当前有效 API 契约。
- 字段、枚举、分页、错误码、权限、幂等性和版本兼容必须明确。
- 契约变更前评估前端、后端、数据、测试、权限和发布影响。
