"""应用组合根：集中装配配置、provider 和各功能服务。"""

from dataclasses import dataclass

from rag_engine.config.settings import Settings
from rag_engine.engine.service import EngineService
from rag_engine.generation.service import GenerationService
from rag_engine.indexing.pgvector import PgVectorSearchIndex
from rag_engine.ingestion.repository import InMemoryIngestTaskRepository
from rag_engine.ingestion.service import IngestionService
from rag_engine.parsing.langchain_parser import LangChainParser
from rag_engine.providers.embeddings import OpenAiCompatibleEmbedding
from rag_engine.providers.llm import OpenAiCompatibleLlm
from rag_engine.providers.object_store import MinioObjectStore
from rag_engine.providers.registry import ProviderRegistry
from rag_engine.rerank.service import RerankService
from rag_engine.retrieval.service import RetrievalService


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

    2026-08-17：真实 provider（MinIO / pgvector / Embedding / LLM）在
    ``pgvector_enabled=True`` 且配置完整时自动装配；缺配置则保持 minimal，
    摄取/问答 fail-closed（不假报已解析）。
    """
    registry = providers or ProviderRegistry.minimal()
    rerank = RerankService(enabled=settings.reranker_provider == "local")

    if providers is None and settings.pgvector_enabled:
        registry = _build_real_registry(settings)

    return ApplicationContainer(
        settings=settings,
        providers=registry,
        ingestion=IngestionService(
            InMemoryIngestTaskRepository(max_tasks=settings.max_in_memory_tasks),
            object_store=registry.object_store,
            parser=registry.parser,
            search_index=registry.search_index,
        ),
        retrieval=RetrievalService(search_index=registry.search_index),
        rerank=rerank,
        generation=GenerationService(
            search_index=registry.search_index,
            llm=registry.llm,
            llm_model=settings.llm_model,
        ),
        engine=EngineService(registry, local_reranker_enabled=rerank.available),
    )


def _build_real_registry(settings: Settings) -> ProviderRegistry:
    """构造连接真实基础设施的 provider 注册表。

    任一必需配置缺失时保持 minimal（宁可 fail-closed，不半装配导致假可用）。
    """
    if not (
        settings.database_url
        and settings.minio_access_key
        and settings.minio_secret_key
        and settings.embedding_api_key
        and settings.llm_api_key
    ):
        return ProviderRegistry.minimal()

    object_store = MinioObjectStore(
        endpoint=settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        bucket=settings.minio_bucket,
    )
    embedding = OpenAiCompatibleEmbedding(
        base_url=settings.embedding_base_url,
        api_key=settings.embedding_api_key,
        dimension=settings.embedding_dimension,
    )
    search_index = PgVectorSearchIndex(
        database_url=settings.database_url,
        embedding=embedding,
        embedding_model=settings.embedding_model,
        min_score=settings.retrieval_min_score,
        embedding_batch_size=settings.embedding_batch_size,
    )
    llm = OpenAiCompatibleLlm(
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        timeout_ms=settings.llm_timeout_ms,
    )
    return ProviderRegistry(
        object_store=object_store,
        parser=LangChainParser(object_store),
        embedding=embedding,
        search_index=search_index,
        llm=llm,
    )
