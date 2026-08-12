# 三端知识库与 Agent 最小 MVP 开发计划

> **文档状态**：建议实施 · **版本**：v0.2.0 · **负责人**：zhanghuaiwei · **最近更新**：2026-08-12
> **适用范围**：`web/`、`service/`、`rag-engine/`
> **现有契约**：[`server.openapi.yaml`](../../../../api/server.openapi.yaml) · [`rag-engine.openapi.yaml`](../../../../api/rag-engine.openapi.yaml)

## 1. MVP 业务流程

```mermaid
flowchart LR
  A["用户登录"] --> B["创建知识库"]
  B --> C["上传 Markdown/TXT"]
  C --> D["Java 保存文档和任务"]
  D --> E["Python 解析、分块、向量化、建立索引"]
  E --> F["文档可检索"]
  F --> G["用户发起提问"]
  G --> H["Java 校验用户和知识库权限"]
  H --> I["Python Agent 调用知识检索工具"]
  I --> J["LLM 根据检索内容生成答案"]
  J --> K["Java 通过 SSE 转发"]
  K --> L["Web 展示答案和引用"]
```

MVP 最终只要求完成一个闭环：

> 用户登录 → 创建知识库 → 上传一份 Markdown/TXT → 等待处理完成 → 发起提问 →
> Agent 检索知识 → 返回带来源的答案；没有证据时返回 `NO_ANSWER`。

## 2. MVP 要做哪些功能

### Web

- 使用真实 HTTP API，不再依赖 mock 完成成功路径。
- 完成知识库创建、文档上传、处理状态展示。
- 完成会话创建、问题提交、SSE 回答和引用展示。
- 后端失败时展示真实错误，不静默返回本地 mock 答案。

### Java

- 实现最小知识库、文档、任务和聊天会话用例。
- 使用 PostgreSQL 保存业务数据，使用 MinIO 保存原始文件。
- 调用 Python 的摄取、搜索、问答和健康接口。
- 从认证上下文取得用户和 tenant，并校验知识库访问权限。
- 将 Python SSE 转发给 Web，并保存消息、来源和基本审计信息。

### Python

- 实现 Markdown/TXT 读取、解析、分块和索引。
- 实现关键词检索，再替换为 Embedding 向量检索。
- 接入一个 Embedding Provider 和一个 LLM Provider。
- 实现引用、低置信度和无答案处理。
- 实现一个只读 Agent 工具：`knowledge_search(query, kbIds, topK)`。

### MVP 基础设施

- PostgreSQL：知识库、文档、版本、任务、会话和审计事实数据。
- MinIO：Markdown/TXT 原始文件。
- Python 内存向量索引：只用于学习型 MVP，重启后允许重新摄取恢复。
- 环境变量：数据库、MinIO、Python 地址、模型地址、模型名和密钥引用。

## 3. 第一轮：打通三端问答

### 开发计划

本轮不接数据库、MinIO、Embedding 和 LLM，使用固定演示语料打通
Web → Java → Python → Java → Web。

| 端 | 开发内容 |
| --- | --- |
| Python | 固定 3～5 条演示知识块；实现简单词项检索；输出 `meta/token/sources/final` SSE |
| Java | 实现 `RagEngineHttpClient#health/#routeStatus/#chatStream`；提供开发态内存会话 |
| Web | 切换真实 HTTP chat client；显示流式答案、来源和真实错误 |

### 实施步骤

1. 为 Java → Python health、route-status 调用补正常、超时、5xx、坏 JSON 测试。
2. 实现 `RagEngineHttpClient#health/#routeStatus`，确认跨进程连接可用。
3. Python 根据问题从固定知识块中选出最相关内容。
4. Python 生成确定性演示回答并输出 SSE；无命中时输出 `NO_ANSWER`。
5. Java 实现 `chatStream`，将 Python SSE 映射为 `ChatEventVo`。
6. Java 实现最小会话创建、消息列表和提问，数据暂存在内存。
7. Web HTTP 模式接入真实会话和问答接口，移除静默 mock fallback。

### 验证方案

- 自动化：Python 检索/SSE 测试，Java HTTP/SSE 映射测试，Web SSE 聚合测试。
- 成功场景：提问命中演示语料，Web 显示答案和来源。
- 无答案场景：提问无关问题，返回 `NO_ANSWER`。
- 失败场景：关闭 Python，Web 显示服务不可用，不出现 mock 答案。

完成标准：三端链路可重复运行，SSE 顺序为 `meta → token* → sources → final`。

## 4. 第二轮：接入真实知识库和文档

### 开发计划

本轮用真实 Markdown/TXT 替换固定演示语料，完成知识库创建、上传、解析和检索。

| 端 | 开发内容 |
| --- | --- |
| Java | 实现 KB create/list/get；文档上传/list/get；PostgreSQL 持久化；MinIO 存储；摄取任务轮询 |
| Python | 实现 MinIO ObjectStore、Markdown/TXT Parser、chunker 和内存 SearchIndex |
| Web | 接入知识库创建、文档上传、文档列表和处理状态 |

### 实施步骤

1. 启用 PostgreSQL 和 MinIO 开发环境，确认迁移、bucket 和配置可用。
2. Java 实现最小知识库创建、列表和详情。
3. Java 接收 Markdown/TXT，写入 MinIO，并保存 document/document_version/parse_task。
4. Java 调用 Python `/api/ingest/documents`，轮询摄取任务状态。
5. Python从 MinIO读取对象，完成解析、固定长度分块和内存索引写入。
6. Java根据 Python结果更新文档状态为成功或失败。
7. Web展示上传进度、处理状态和失败原因。

### 验证方案

- 自动化：Java KB/文档服务测试，Python Parser/chunker/index 测试，MinIO 失败测试。
- 成功场景：上传 Markdown/TXT 后状态变为可检索。
- 幂等场景：同一文档版本重复摄取不产生重复 chunk。
- 失败场景：对象不存在或解析失败时任务进入 FAILED，并展示错误原因。
- 恢复场景：Python 重启后可通过重新摄取恢复内存索引。

完成标准：用户上传自己的文档后，可以在该知识库范围内检索到内容。

## 5. 第三轮：实现真实 RAG

### 开发计划

本轮把关键词检索和模板回答替换为 Embedding、向量检索和 LLM 生成。

| 模块 | 开发内容 |
| --- | --- |
| Embedding | 实现一个 OpenAI-compatible `EmbeddingProvider` |
| 检索 | 保存归一化向量，使用余弦相似度返回 TopK |
| LLM | 实现一个 OpenAI-compatible `LlmProvider`，支持流式 token |
| RAG | Prompt 组装、来源引用、置信度、`NO_ANSWER/LOW_CONFIDENCE` |
| Java/Web | 转发并展示 token、sources、final 和模型错误 |

### 实施步骤

1. 增加模型 base URL、API key、Embedding 模型、LLM 模型和 timeout 环境配置。
2. 文档摄取时调用 Embedding Provider 并写入内存向量索引。
3. 提问时生成 query embedding，执行 TopK 余弦相似度检索。
4. 相似度低于阈值时直接返回 `NO_ANSWER`，不调用 LLM 生成伪答案。
5. 有可靠来源时构造 system policy、user question、untrusted context 三段式 Prompt。
6. LLM 流式输出 token，完成后输出来源、置信度和最终状态。
7. Java 保存问答消息和来源，Web展示引用信息。

### 验证方案

- 自动化：Embedding adapter、向量排序、阈值拒答、LLM timeout/429/5xx 和 SSE 测试。
- 准确场景：文档内有明确答案，返回内容和正确来源。
- 拒答场景：文档中无答案，返回 `NO_ANSWER`。
- 引用场景：每个来源包含 documentId、version/chunk 和位置。
- 安全场景：日志中没有 API key、完整文档或敏感 Prompt。
- 质量验证：准备 10～20 个问题，记录检索命中率、引用正确率和拒答正确率。

完成标准：系统能够根据用户上传的文档生成真实、可引用、可拒答的 RAG 回答。

## 6. 第四轮：实现最小 Agent

### 开发计划

本轮在 RAG 已稳定的基础上增加 Agent 决策，只开放一个只读知识检索工具。

```text
agent/
  models.py     AgentState、AgentStep、ToolCall、ToolResult
  tools.py      Tool 接口和 ToolRegistry
  runner.py     plan → act → observe → finish
  prompts.py    工具说明和回答规则
```

### 实施步骤

1. 注册 `knowledge_search(query, kbIds, topK)` 工具，内部复用已有 RAG 检索服务。
2. 模型判断是直接回答还是调用知识检索工具。
3. Agent执行 `plan → tool_call → tool_result → finish` 循环。
4. 限制最多 3 步、最多 2 次工具调用，并设置 timeout 和 Token 上限。
5. 用 Pydantic 校验工具参数；未知工具、非法 JSON、越权 kbId 直接拒绝。
6. 最终答案必须引用真实工具结果；没有来源时返回 `NO_ANSWER`。
7. Java校验用户和 KB 权限，记录 requestId、工具名、耗时、token 和来源摘要。

### 验证方案

- 知识问题：Agent 调用 `knowledge_search` 后返回带引用答案。
- 闲聊问题：Agent可以直接回答，不执行无意义检索。
- 无答案问题：工具无命中时返回 `NO_ANSWER`。
- 工具安全：请求调用 Shell、SQL、任意 URL 时，Agent 无对应工具可用。
- 越权验证：传入无权限 kbId 时 Java 拒绝，Python 不执行检索。
- 预算验证：超过步数、工具次数、timeout 或 Token 限制时稳定终止。

完成标准：Agent能够自主决定是否调用知识检索工具，并在权限和预算范围内返回答案。

## 7. 每轮验证命令

```bash
# Java：使用 JDK 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
cd service && mvn -B -q test

# Python
cd rag-engine && uv run ruff check . && uv run pytest -q

# Web
cd web && pnpm test && pnpm typecheck
```

每轮都必须保留三个可重复场景：成功、无答案、依赖失败。需要增加或修改公共字段时，
先更新并评审 OpenAPI，再修改 Java、Python 和 Web。

## 8. MVP 暂时不做

- PDF/OCR/Office、多模态和内容连接器。
- 多 Agent、长期记忆和 Agent 间通信。
- Shell、SQL、任意网络访问或其他写操作工具。
- 动态模型路由、模型微调和大规模向量集群。
- SCIM、法律保全、计费、多区域灾备等完整治理能力。

完成第四轮，即完成当前学习型 Agent MVP。
