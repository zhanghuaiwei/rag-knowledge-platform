# rag-engine — RAG 引擎

> 通用企业知识库平台的 Python 计算引擎，负责解析、分块、Embedding、检索、精排和生成。当前已提供与 v0.1 内部 OpenAPI 对齐的最小可运行实现；真实对象存储、搜索索引、模型 provider、服务身份和授权上下文尚未接入，不能作为生产 RAG 使用。

- 权威设计：[`../docs/03-详细设计.md`](../docs/03-详细设计.md)、[`../docs/05-技术选型.md`](../docs/05-技术选型.md)、[`../docs/06-架构方案.md`](../docs/06-架构方案.md)
- 内部契约：[`../docs/api/rag-engine.openapi.yaml`](../docs/api/rag-engine.openapi.yaml)（v0.1，待升级/冻结）
- 实现与调用流程：[`../docs/modules/enterprise-generalization/design/rag-engine-minimal-implementation.md`](../docs/modules/enterprise-generalization/design/rag-engine-minimal-implementation.md)

## 当前能力

| 端点 | 当前行为 |
| --- | --- |
| `POST /api/ingest/documents` | 202 受理；无 provider 时任务随后明确失败 |
| `GET /api/ingest/tasks/{id}` | 查询进程内任务快照；未知任务 404 |
| `POST /api/ingest/delete` | 无索引时幂等返回 `deletedCount=0` |
| `POST /api/query/search` | 无授权索引时返回稳定空分页 |
| `POST /api/query/chat` | SSE `meta -> final(no_answer)`，不伪造 token/引用 |
| `POST /api/query/rerank` | 确定性本地词项覆盖率排序，仅供联调 |
| `GET /api/engine/health` | 返回 `degraded` 和各能力可用性 |
| `POST /api/engine/route-status` | 未配置模型路由时返回不可用 |
| `GET /healthz` | 仅表示 FastAPI 进程存活 |

最小实现遵循“显式降级、不伪造结果”：没有真实 provider 时，不把 objectKey 当正文、不伪造向量数量、搜索命中、模型回答或引用。

## 技术栈

| 项 | 版本 |
| --- | --- |
| Python | 3.12+ |
| FastAPI | 0.115+ |
| Pydantic / pydantic-settings | 2.x |
| Uvicorn | 0.30+ |
| 依赖管理 | uv |
| 测试 / 静态检查 | pytest / ruff |

## 按功能组织

项目使用 package-by-feature：一个业务功能一个包，功能包内部自行维护 `router`、
`schemas`、`service`、`models` 和 `ports`；跨功能复用且不含业务编排的内容才进入
`common`。`api` 只聚合路由和解析依赖，`config` 只负责环境配置，`providers` 只负责
外部适配器注册。

| 包 | 功能职责 |
| --- | --- |
| `ingestion` | 摄取受理、任务状态、后台处理和派生索引删除 |
| `retrieval` | 授权检索模型、搜索请求、分页和检索流水线端口 |
| `rerank` | 本地联调精排和生产 Reranker 端口 |
| `generation` | 问答请求、SSE 事件、LLM 生成端口 |
| `engine` | provider readiness 和模型路由状态 |
| `health` | 进程 liveness |
| `auth` | 工作负载身份和短期检索授权上下文边界 |
| `parsing` / `safety` / `indexing` | 解析、内容安全、Embedding 与索引功能端口 |
| `common` / `config` / `observability` | 公共 DTO、运行配置、日志 |
| `providers` | 外部 provider 实例的显式注册，不包含业务规则 |

关键原则：

- rag-engine 只做重计算和派生索引，不承担用户、租户、文档状态等业务事实源。
- 生产查询只接受已认证服务和签名的短期 `RetrievalAccessContext`；当前 v0.1 契约尚未包含该能力。
- 领域层只依赖端口，供应商 SDK、凭证、超时和错误映射属于 adapter/provider。
- 索引可重建；模型、分块和维度由不可变 `indexProfileVersion` 管理。

## 目录结构

```text
rag-engine/
├── .env.example
├── pyproject.toml
├── uv.lock
├── src/rag_engine/
│   ├── __main__.py                     # 读取 host/port 等环境变量并启动 Uvicorn
│   ├── main.py                         # FastAPI 应用工厂
│   ├── container.py                    # 配置、provider 与功能服务的组合根
│   ├── api/                            # 路由聚合和依赖解析
│   ├── common/                         # API 公共基类和跨功能 DTO
│   ├── config/                         # pydantic-settings 环境配置
│   ├── ingestion/                      # router/schemas/models/repository/service
│   ├── retrieval/                      # router/schemas/models/ports/service
│   ├── rerank/                         # router/schemas/models/ports/service
│   ├── generation/                     # router/schemas/models/ports/service
│   ├── engine/                         # router/schemas/service
│   ├── health/                         # liveness router
│   ├── auth/ parsing/ safety/ indexing/ # 已定义功能点的模型与端口
│   ├── providers/                      # provider 注册表和基础设施端口
│   └── observability/                  # 日志配置
└── tests/
    ├── test_internal_api.py            # v0.1 端点兼容行为
    ├── test_settings.py                # 环境变量和装配
    └── test_package_structure.py       # 功能包与空文件约束
```

所有包的 `__init__.py` 都声明公共导出；结构测试禁止空 Python 文件和退回旧的
`application/domain/ports` 横向分包。

## 快速开始

### 环境要求与安装

```bash
cd rag-engine
uv sync
cp .env.example .env
```

需要 Python 3.12+ 和 `uv`。

### 本地运行

```bash
cd rag-engine
uv run python -m rag_engine
# 或安装后的命令：uv run rag-engine
```

该入口会读取 `.env` 和 `RAG_ENGINE_*` 环境变量。默认监听 `127.0.0.1:8000`；需要热
更新时设置 `RAG_ENGINE_RELOAD=true`。验证 liveness 与详细能力状态：

```bash
curl http://localhost:8000/healthz
# {"status":"ok","service":"rag-engine","phase":"minimal"}

curl http://localhost:8000/api/engine/health
# status=degraded；embedding-provider/llm-provider unavailable
```

提交最小摄取任务：

```bash
curl -X POST http://localhost:8000/api/ingest/documents \
  -H 'Content-Type: application/json' \
  -d '{"documentId":1,"objectKey":"quarantine/document-1/v1","kbConfig":{},"tenantId":1,"kbId":1,"versionNo":1}'
```

返回 taskId 后调用 `GET /api/ingest/tasks/{id}`。由于真实 provider 未配置，任务预期为 `FAILED`；这是当前设计行为，不是环境异常。

### 环境变量

| 环境变量 | 默认值 | 实际作用 |
| --- | --- | --- |
| `RAG_ENGINE_SERVICE_NAME` | `rag-engine` | FastAPI 标题和 liveness 服务名 |
| `RAG_ENGINE_SERVICE_VERSION` | `0.2.0-SNAPSHOT` | FastAPI/OpenAPI 应用版本 |
| `RAG_ENGINE_ENVIRONMENT` | `local` | 部署环境标识：local/test/development/staging/production |
| `RAG_ENGINE_HOST` | `127.0.0.1` | `python -m rag_engine` 的监听地址 |
| `RAG_ENGINE_PORT` | `8000` | Uvicorn 监听端口 |
| `RAG_ENGINE_LOG_LEVEL` | `INFO` | Python/Uvicorn 日志级别 |
| `RAG_ENGINE_RELOAD` | `false` | 本地 Uvicorn 热更新 |
| `RAG_ENGINE_DOCS_ENABLED` | `true` | 是否开放 `/docs`、`/redoc`、`/openapi.json` |
| `RAG_ENGINE_ROOT_PATH` | 空 | 反向代理路径前缀 |
| `RAG_ENGINE_MAX_IN_MEMORY_TASKS` | `1024` | 开发态内存任务仓库上限 |
| `RAG_ENGINE_RERANKER_PROVIDER` | `local` | `local` 启用联调精排，`disabled` 关闭 |
| `RAG_ENGINE_ENV_FILE` | `.env` | 在进程环境中指定其他配置文件路径 |

`.env`、`.env.*` 均被仓库忽略，只有 `.env.example` 可提交。当前没有真实对象存储、
索引和模型 adapter，因此不预置无消费者的 endpoint/key；相应功能实现时，配置应进入
对应 provider 功能包，并通过 Secret 注入和启动校验接入。

### 测试与静态检查

```bash
uv run pytest -q
uv run ruff check .
```

测试覆盖全部 8 个内部端点、参数拒绝、摄取失败闭环、任务 404、删除幂等、空搜索
分页、SSE 顺序、精排稳定性、环境变量、应用装配、任务容量和功能包结构。

## 生产接入前 TODO

- [ ] 先升级并冻结 v0.2 内部 OpenAPI：工作负载认证、`RetrievalAccessContext`、tenant/version/indexProfile、幂等、错误和 SSE。
- [ ] 用持久化 task/outbox + worker 替换 FastAPI BackgroundTasks 和进程内任务仓库。
- [ ] 实现 ContentSafety、ObjectStore、Parser/OCR、Embedding、SearchIndex 的 adapter 与契约测试。
- [ ] 实现授权预过滤、候选二次授权、RRF/精排、低置信拒答、LLM 流、引用和输出安全。
- [ ] 实现 provider 路由、数据分类/区域/预算策略、超时、重试/熔断、指标和追踪。
- [ ] 实现 Java `RagEngineHttpClient` 和上层业务用例，完成端到端与权限泄漏测试。

代码中的 TODO 均带具体类和方法名，分别位于对应功能包的 Service 边界；新实现不得
绕过 OpenAPI，也不得把浏览器传入的 tenant/角色当作可信授权上下文。
