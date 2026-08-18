"""应用组合根：集中装配配置、provider 和各功能服务。

provider 路由的**单一事实来源**（Single Source of Truth）：本文件的
:func:`_build_llm_from_settings` / :func:`_build_embedding_from_settings`
/ :func:`_build_safety_from_settings` 三个函数分别按
``Settings.llm_provider_type`` / ``embedding_provider_type`` / ``safety_provider_type``
路由到具体实现。任何 provider 切换需求都从环境变量改，不改代码。
"""

from __future__ import annotations

from dataclasses import dataclass

from rag_engine.config.settings import (
    EmbeddingProviderType,
    LlmProviderType,
    SafetyProviderType,
    Settings,
)
from rag_engine.engine.service import EngineService
from rag_engine.generation.service import GenerationService
from rag_engine.indexing.pgvector import PgVectorSearchIndex
from rag_engine.ingestion.repository import InMemoryIngestTaskRepository
from rag_engine.ingestion.service import IngestionService
from rag_engine.parsing.langchain_parser import LangChainParser
from rag_engine.providers.embeddings import NoopEmbedding, OpenAiCompatibleEmbedding
from rag_engine.providers.llm import AnthropicLlm, NoopLlm, OpenAiCompatibleLlm
from rag_engine.providers.object_store import MinioObjectStore
from rag_engine.providers.registry import ProviderRegistry
from rag_engine.rerank.service import RerankService
from rag_engine.retrieval.service import RetrievalService
from rag_engine.safety.noop_provider import NoopSafetyProvider


@dataclass(frozen=True, slots=True)
class ApplicationContainer:
    """FastAPI 路由依赖的显式服务集合。"""

    settings: Settings
    providers: ProviderRegistry
    ingestion: IngestionService
    retrieval: RetrievalService
    rerank: RerankService
    generation: GenerationService
    engine: EngineService


def build_container(
    settings: Settings,
    *,
    providers: ProviderRegistry | None = None,
) -> ApplicationContainer:
    """按运行配置装配服务；``pgvector_enabled=False`` 时保持 minimal（无外部连接）。

    路由规则：
    * 显式传 ``providers`` 时（测试）直接使用，不再自动装配；
    * 否则 ``pgvector_enabled=True`` 且必需配置完整时按 provider_type 路由装配真实 provider；
    * 任一必需配置缺失（如 api_key 为空）回退到 minimal（fail-closed，不假报可用）。
    """
    rerank = RerankService(enabled=settings.reranker_provider_type != "disabled")

    registry = providers
    if registry is None and settings.pgvector_enabled:
        registry = _build_real_registry(settings)
    if registry is None:
        registry = ProviderRegistry.minimal()

    return ApplicationContainer(
        settings=settings,
        providers=registry,
        ingestion=IngestionService(
            InMemoryIngestTaskRepository(max_tasks=settings.max_in_memory_tasks),
            object_store=registry.object_store,
            parser=registry.parser,
            search_index=registry.search_index,
        ),
        retrieval=RetrievalService(
            search_index=registry.search_index,
            # 全文搜索融合精排复用本地词项覆盖 RerankService（不可用时自动退化为纯向量序）。
            reranker=rerank if rerank.available else None,
        ),
        rerank=rerank,
        generation=GenerationService(
            search_index=registry.search_index,
            llm=registry.llm,
            llm_model=settings.llm_model,
        ),
        engine=EngineService(registry, local_reranker_enabled=rerank.available),
    )


# =========================================================================
# provider 路由：每个 provider_type → 具体构造器
# 新增一种 provider 时只需要在本文件对应函数里加一个分支。
# =========================================================================


def _build_llm_from_settings(settings: Settings):
    """按 ``settings.llm_provider_type`` 构造 LLM provider。

    返回 ``None`` 表示 fail-closed 不装配（缺密钥或类型不支持）。
    """
    t = settings.llm_provider_type
    api_key = settings.llm_api_key
    # NOOP 永远可用（不发网络请求），即使缺 api_key
    if t == LlmProviderType.NOOP:
        return NoopLlm()
    # 非 NOOP 的情况下，缺 key 时 fail-closed
    if not api_key:
        return None
    if t in {
        LlmProviderType.OPENAI_COMPATIBLE,
        LlmProviderType.DASHSCOPE,
        LlmProviderType.SILICONFLOW,
        LlmProviderType.DEEPSEEK,
        LlmProviderType.ZHIPU,
        # 用户显式设置了非空 llm_base_url 时，Anthropic 也可用 OpenAI 兼容代理
    } and (settings.llm_base_url or t != LlmProviderType.OPENAI_COMPATIBLE):
        try:
            return OpenAiCompatibleLlm(
                base_url=settings.llm_base_url,
                api_key=api_key,
                timeout_ms=settings.llm_timeout_ms,
                extra_headers=settings.llm_extra_headers,
            )
        except Exception:
            return None
    if t == LlmProviderType.ANTHROPIC:
        # 原生 Anthropic（需要 anthropic 包），缺包时 fail-closed 返回 None
        try:
            return AnthropicLlm(
                api_key=api_key,
                timeout_ms=settings.llm_timeout_ms,
                model=settings.llm_model,
                extra_headers=settings.llm_extra_headers,
            )
        except Exception:
            # ImportError / RuntimeError 等都吞掉，让调用方继续走 minimal registry；
            # 显式错误日志留给健康检查 / cap_use_services 用 capability 告知。
            return None
    # 兜底：未知类型或缺 base_url 时不装配
    return None


def _build_embedding_from_settings(settings: Settings):
    """按 ``settings.embedding_provider_type`` 构造 Embedding provider。"""
    t = settings.embedding_provider_type
    api_key = settings.embedding_api_key
    if t == EmbeddingProviderType.NOOP:
        return NoopEmbedding(dimension=settings.embedding_dimension)
    if not api_key:
        return None
    if t in {
        EmbeddingProviderType.OPENAI_COMPATIBLE,
        EmbeddingProviderType.DASHSCOPE,
        EmbeddingProviderType.SILICONFLOW,
        EmbeddingProviderType.ZHIPU,
    } and (settings.embedding_base_url or t != EmbeddingProviderType.OPENAI_COMPATIBLE):
        try:
            return OpenAiCompatibleEmbedding(
                base_url=settings.embedding_base_url,
                api_key=api_key,
                dimension=settings.embedding_dimension,
                extra_headers=settings.embedding_extra_headers,
            )
        except Exception:
            return None
    return None


def _build_safety_from_settings(settings: Settings):
    """按 ``settings.safety_provider_type`` 构造内容安全 provider。"""
    if settings.safety_provider_type == SafetyProviderType.NOOP:
        return NoopSafetyProvider()
    # SafetyProviderType.DISABLED 或未知 → 不装配（container 中 registry.content_safety 为 None）
    return None


def _build_real_registry(settings: Settings) -> ProviderRegistry | None:
    """按 Settings 构造连接真实基础设施的 provider 注册表。

    任一必需配置缺失时返回 ``None``，调用方会回退到 :meth:`ProviderRegistry.minimal`。
    """
    # 必需条件：MinIO 凭证 + DB URL
    if not (settings.database_url and settings.minio_access_key and settings.minio_secret_key):
        return None

    # provider 路由（每个都可能失败，单独判定）
    embedding = _build_embedding_from_settings(settings)
    llm = _build_llm_from_settings(settings)
    safety = _build_safety_from_settings(settings)

    # 最小闭环需要 embedding 和 llm 都能装上；缺任何一个依然保持 minimal
    if embedding is None or llm is None:
        return None

    object_store = MinioObjectStore(
        endpoint=settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        bucket=settings.minio_bucket,
    )
    search_index = PgVectorSearchIndex(
        database_url=settings.database_url,
        embedding=embedding,
        embedding_model=settings.embedding_model,
        min_score=settings.retrieval_min_score,
        embedding_batch_size=settings.embedding_batch_size,
    )
    return ProviderRegistry(
        object_store=object_store,
        secret_resolver=None,
        content_safety=safety,
        parser=LangChainParser(object_store),
        embedding=embedding,
        search_index=search_index,
        reranker=None,  # RerankService 当前由本地词项精排直接提供，不通过 registry.llm 形态
        llm=llm,
    )
