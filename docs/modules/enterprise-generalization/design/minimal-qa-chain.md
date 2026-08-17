# 最小知识库问答链路（上传 → 摄取 → pgvector → LLM → SSE）

> **文档状态**：实现说明 + 部署 runbook · **版本**：v0.1 · **记录时间**：2026-08-17
> **适用范围**：`web/`、`service/`、`rag-engine/`、`deploy/ddl/migrations/V0.6`
> **契约**：[`server.openapi.yaml`](../../../api/server.openapi.yaml) · [`rag-engine.openapi.yaml`](../../../api/rag-engine.openapi.yaml)

---

## 1. 目标闭环

```
前端提问 → Java(/chats/{id}/messages) → rag-engine POST /api/query/chat
   → pgvector 检索 top_k（kb 过滤 + 相似度阈值）
   → RAG 三段式 Prompt → LLM 流式 token
   → SSE: meta → token* → sources → final → Java 回写 chat_message / chat_message_source → 前端展示
```

同时把既有上传链路的「摄取」补齐：

```
上传 complete → parse_task(QUEUED) + outbox
   → [Java 调度器] 轮询 QUEUED → rag-engine POST /api/ingest/documents
   → [rag-engine] MinIO 读原文 → langchain 分块 → Embedding → pgvector(chunk_meta) 落库 → SUCCESS
   → [Java 调度器] 轮询 RUNNING → 回写 document_version.ingest_status = READY/FAILED
```

## 2. 各端改动清单（本次实现）

### 2.1 rag-engine（Python）——新增真实 provider 与流水线

| 文件 | 说明 |
|---|---|
| `config/settings.py` | 新增 pgvector / MinIO / Embedding / LLM 配置（`RAG_ENGINE_*` 前缀） |
| `providers/embeddings.py` | OpenAI 兼容 Embedding（DashScope text-embedding-v3，L2 归一化，1024 维） |
| `providers/llm.py` | OpenAI 兼容 LLM（`stream()` 懒迭代器逐 token 输出） |
| `providers/object_store.py` | MinIO 对象读取（object_key = S3 对象名） |
| `parsing/langchain_parser.py` | md/txt/csv/html → 正文块（stdlib 解码，**分块用 langchain**） |
| `indexing/pgvector.py` | `chunk_meta` 直写 + HNSW 余弦检索（psycopg.sql 参数化，无注入面） |
| `ingestion/service.py` | 真实流水线：读对象 → langchain 分块 → embedding → pgvector → SUCCESS |
| `generation/service.py` | RAG 问答：检索 → 三段式 Prompt → LLM 流 → SSE 事件 |
| `ingestion/models.py` `schemas.py` | `IngestRequest` 增加 `versionId`；快照携带对象/版本上下文 |
| `generation/schemas.py` | `QueryChatRequest` 增加 `tenantId` |

### 2.2 service（Java）

| 文件 | 说明 |
|---|---|
| `modules/rag/port/RagEnginePort.java` | `parseDocument` 增加 `versionId/kbId`；`chatStream` 改回调 `ChatStreamEvent`；`defaultKbConfig()` 静态方法 |
| `modules/rag/port/ChatStreamEvent.java` | SSE 事件记录（type + data） |
| `modules/rag/adapter/RagEngineHttpClient.java` | 真实对接：ingest 提交 / 任务查询 / SSE 问答 / 健康；search/rerank 仍为桩 |
| `modules/ingestion/service/impl/IngestionDispatchScheduler.java` | 定时轮询 parse_task → 投递 rag-engine → 回写 document_version/parse_task |
| `modules/document/service/DocumentService.java` + Impl | 新增 `ingestSource(versionId)` / `updateIngestStatus(...)`（供调度器跨模块协作） |
| `modules/conversation/service/impl/SearchChatServiceImpl.java` | 会话 CRUD + ask（SSE 转发 + 落库）全部实现 |
| `modules/conversation/controller/SearchChatController.java` | 随 `ragkb.db.enabled` 条件挂载（免库脚手架不加载） |
| `config/DatabaseConfig.java` | 增加 `@EnableScheduling`（随 db.enabled 条件启用） |

### 2.3 DB / 部署

- `deploy/ddl/migrations/V0.6__pgvector_chunks.sql`：启用 pgvector + `chunk_meta.embedding vector(1024)` + HNSW 余弦索引
- `deploy/compose/docker-compose.yml`：postgres 镜像换 `pgvector/pgvector:pg16-alpine`（pgdata 卷可复用，数据不丢）
- `rag-engine/.env.example`：新增全部运行配置模板

### 2.4 前端（web）

**无改动**。上传弹窗、文档状态、会话/问答、SSE 聚合均为既有实现；本次仅打通其后端链路。

## 3. 部署 runbook（按顺序执行）

> 前置：远程服务器 `8.152.103.199` 已跑 compose；本地已装 `uv` / JDK 21 / pnpm。

### 3.1 数据库：pgvector + 迁移

```bash
# ① 服务器上重建 postgres（换 pgvector 镜像，pgdata 卷保留）
cd deploy/compose
docker compose up -d --force-recreate postgres
# ② 用 DBeaver 连 8.152.103.199:5432/ragkb（postgres / superuser）
#    执行 deploy/ddl/migrations/V0.6__pgvector_chunks.sql（纯 SQL 幂等，可安全重跑）
```

### 3.2 rag-engine：配置 + 启动（本机）

```bash
cd rag-engine
cp .env.example .env
# 填写 .env：RAG_ENGINE_DATABASE_URL / MINIO_* / EMBEDDING_API_KEY / LLM_API_KEY
# 例：
#   RAG_ENGINE_DATABASE_URL=postgresql://postgres:/IH3jux14WKmcLoJGfTww9Pr@8.152.103.199:5432/ragkb
#   RAG_ENGINE_MINIO_ENDPOINT=http://8.152.103.199:9000
#   RAG_ENGINE_MINIO_ACCESS_KEY=minioadmin
#   RAG_ENGINE_MINIO_SECRET_KEY=<ignore/.env 的 MINIO_ROOT_PASSWORD>
#   RAG_ENGINE_MINIO_BUCKET=kb-bucket-0814
#   RAG_ENGINE_EMBEDDING_API_KEY=<DashScope key>
#   RAG_ENGINE_LLM_API_KEY=<DashScope key>
uv run python -m rag_engine            # 监听 127.0.0.1:8000
# 自检：curl http://localhost:8000/api/engine/health → status=ok（embedding/llm available=true）
```

### 3.3 Java service：重启（IntelliJ 里重启 RagkbServiceApplication）

- 环境变量保持现状即可（DB/MinIO/Redis 指向 `8.152.103.199`）。
- `rag-engine.base-url` 默认 `http://localhost:8000`，无需改动。
- 重启后触发：已存在的 2 条 QUEUED parse_task 会被调度器在 5s 内投递摄取。

### 3.4 前端

- `web` 无需改动；登录后打开文档库/知识库，等待文档状态从 `PARSING` → `READY`。
- 进入会话页（chat）→ 选择对应知识库 → 提问 → 看流式答案与引用来源。

## 4. 验证命令

```bash
# Python
cd rag-engine && uv run ruff check . && uv run pytest -q        # 15 个用例
# Java（JDK 21）
export JAVA_HOME=/Users/zhanghuaiwei/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home
cd service && mvn -B -q test                                     # 98 个用例
# 前端（如改动前端再跑）
cd web && pnpm typecheck && pnpm test
```

## 5. 已知限制与迭代点（留给人工）

| 项 | 现状 | 后续方向 |
|---|---|---|
| 安全扫描 | 最小实现直接跳过，`QUARANTINED→PARSING` 即投递 | 接 GKB-03 扫描流水线（quarantine→SCANNING） |
| outbox | 仍写入但不消费（调度器直接轮询 parse_task） | 换 transactional outbox 消费者 + worker |
| parse_task.error_detail | 只写 error_code，不写 JSONB 明细 | 按 OutboxEventMapper 的 CAST 模式补 |
| Embedding 维度 | 固定 1024（text-embedding-v3） | 换模型需改 V0.6 的 vector(1024) + 重摄取 |
| 全文搜索 `/api/query/search` | 仍返回空（最小闭环走问答） | 复用 SearchIndex.search 补检索端点 |
| PDF/Office | 摄取返回 FAILED（仅支持 md/txt/csv/html） | 接入 langchain_community 专用 loader |
| 多轮/记忆 | history 截最近 N 条拼入 Prompt | 接正式记忆存储 |
| 用量/成本 | token 为字符估算，cost=0 | 接 usage_daily / LLM usage 统计 |
| 失败重试 | FAILED 后保持失败，需 reparse 端点重试 | 调度器按 attempt_count 自动重试 |

## 6. 参考

- 上传数据流：[`document-upload-data-flow.md`](./document-upload-data-flow.md)
- MVP 四轮计划：[`../../tasks/mvp/README.md`](../../tasks/mvp/README.md)
- rag-engine 契约：[`rag-engine.openapi.yaml`](../../../api/rag-engine.openapi.yaml)
