# rag-engine — RAG 引擎

> 通用企业知识库平台的 **Python RAG 引擎**:解析 / 分块 / Embedding / 检索 / 精排 / 生成。当前为 v0.2 架构骨架,仅提供 `/healthz` 探针;真实端点将在 `server → rag-engine` 内部契约(RetrievalAccessContext / 服务身份认证)冻结后实现。

- 权威设计:`../docs/03-详细设计.md`、`../docs/05-技术选型.md`、`../docs/06-架构方案.md`
- 内部契约(待补):`../docs/api/rag-engine.openapi.yaml`

## 技术栈

| 项 | 版本 |
| --- | --- |
| Python | 3.12+ |
| FastAPI | 0.115+ |
| Pydantic / pydantic-settings | 2.x |
| Uvicorn | 0.30+ |
| 依赖管理 | uv |
| 测试 / 静态检查 | pytest / ruff |

## 架构分层

```text
api            HTTP 路由(FastAPI):/healthz、未来的 ingest/query/task
   │
application    用例编排(仅接受已认证服务 + 签名授权上下文)
   │
domain         领域模型(ContentBlock、IndexProfile、RetrievalAccessContext ...)
   │
ports          端口(接口):EmbeddingProvider / RerankerProvider / LlmProvider /
               ParserProvider / SearchIndex / ObjectStore / SecretResolver
   │
providers      provider 适配(本地/云端模型、OCR、解析器,可替换)
```

关键原则:

- **只做重计算任务**:解析、OCR、embedding、检索、精排、生成;**不承担业务真相**。
- **认证边界**:只接受 `server` 等已认证服务调用,并校验签名的 `RetrievalAccessContext`,不能只看来源 IP。
- **provider 中立**:领域层只依赖 `ports`,默认实现只是可替换 adapter;高敏内容是否允许外发由租户策略判定。
- 索引是可重建派生数据,不在引擎内持有业务事实源。

## 目录结构

```text
rag-engine/
├── pyproject.toml          # 工程配置 + ruff/pytest 规则
├── uv.lock
├── src/rag_engine/
│   ├── main.py             # FastAPI 入口 + /healthz
│   ├── api/                # 路由层
│   ├── auth/               # 服务身份 + RetrievalAccessContext 校验
│   ├── parsing/            # 解析 / OCR
│   ├── ingestion/          # 摄取编排(幂等补偿)
│   ├── indexing/           # 分块 / embedding / 索引
│   ├── retrieval/          # 检索 / 融合 / 过滤
│   ├── rerank/             # 精排
│   ├── generation/         # 生成 / 引用
│   ├── safety/             # 内容安全
│   ├── providers/          # 外部 provider 适配
│   └── observability/      # OpenTelemetry 等
└── tests/
    └── test_health.py      # 健康检查测试
```

## 快速开始

### 环境要求

- Python 3.12+、[uv](https://docs.astral.sh/uv/)。

### 安装依赖

```bash
cd rag-engine && uv sync
```

### 本地运行

```bash
cd rag-engine && uv run uvicorn rag_engine.main:app --reload
# 默认端口 8000
```

### 验证

```bash
uv run uvicorn rag_engine.main:app &   # 或另开终端
curl http://localhost:8000/healthz
# {"status":"ok","service":"rag-engine","phase":"scaffold"}
```

### 测试与静态检查

```bash
uv run pytest -q        # 测试
uv run ruff check .     # lint
```

## 提交规范

- 新功能先确认需求与唯一内部契约(以 `../docs/api/rag-engine.openapi.yaml` 为准),再实现。
- 提交信息遵循仓库 `CONTRIBUTING.md` 的 Conventional Commits;提交前通过 `make lint / test / security`。

## 当前状态与后续

- [x] FastAPI 工程、`/healthz` 探针、包结构(auth / parsing / ingestion / indexing / retrieval / rerank / generation / safety / providers / observability)
- [ ] 冻结 `server → rag-engine` 内部契约(RetrievalAccessContext、服务认证、ingest/query/delete/task)
- [ ] 按契约实现解析 / 检索 / 生成端口与默认 provider
