# 安全策略

`rag-knowledge-platform` 面向企业知识库场景,身份、权限、数据驻留与审计是硬约束。本项目严格遵守以下安全红线:

- 绝不硬编码密钥 —— API Key / 密码 / Token 一律通过环境变量或 Secret Manager 管理。
- 绝不提交 `.env`、`*.pem`、`*.key`、`credentials.json` 等敏感文件。
- 认证授权只认服务端决策(PDP/PEP),页面按钮与网关路由不构成最终授权。
- 不绕过安全头部(CSP / X-Frame-Options / HSTS)与审计日志。

## 报告漏洞

**请勿在公开 Issue / PR 中提交安全漏洞。** 请通过以下方式负责任披露:

1. 私有联系仓库维护者(zhanghuaiwei)或发送私有安全说明;
2. 或使用 GitHub 的 [Private vulnerability reporting](https://docs.github.com/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability) 功能(如已启用)。

报告请包含:

- 受影响组件与版本(`service/` / `rag-engine/` / `web/` / `deploy/ddl/`);
- 漏洞类型与影响面(数据泄露、越权、注入、供应链等);
- 可复现步骤与最小 PoC;
- 建议修复方向(如有)。

## 响应时间

| 阶段 | 目标时限 |
| --- | --- |
| 确认收到 | 3 个工作日 |
| 严重性评估 | 5 个工作日 |
| 修复方案 | 视严重性,严重漏洞优先发布 |

## 安全相关提交要求

涉及**认证/授权/加密/支付/数据驻留**的 PR 必须:

- 附威胁模型或安全说明;
- 通过 `make security`(密钥扫描)与 CI 全部检查;
- 由维护者人工审查(参照全局工程规则"谨慎区:⚠️ HUMAN REVIEW REQUIRED")。

## 部署安全基线

- 生产禁用 `latest` 镜像与运行时从不可信 URL 下载模型,固定 digest/revision。
- 数据库 runtime role 与 migration owner 分离,不授予 `BYPASSRLS`。
- Webhook 与 Web connector 仅允许 `https`,出站做 SSRF 防护。
- 日志禁止记录 token、API Key、connector secret、完整问题/原文或 DLP 命中内容。

详见 [`docs/06-架构方案.md`](docs/06-架构方案.md) 与 [`docs/09-部署运维指南.md`](docs/09-部署运维指南.md)。
