"""应用组合根：集中装配配置、provider 和各功能服务。"""

from dataclasses import dataclass

from rag_engine.config.settings import Settings
from rag_engine.engine.service import EngineService
from rag_engine.generation.service import GenerationService
from rag_engine.ingestion.repository import InMemoryIngestTaskRepository
from rag_engine.ingestion.service import IngestionService
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
    """按运行配置构建最小应用服务，外部 provider 必须显式注入。"""
    registry = providers or ProviderRegistry.minimal()
    rerank = RerankService(enabled=settings.reranker_provider == "local")
    return ApplicationContainer(
        settings=settings,
        providers=registry,
        ingestion=IngestionService(
            InMemoryIngestTaskRepository(max_tasks=settings.max_in_memory_tasks)
        ),
        retrieval=RetrievalService(),
        rerank=rerank,
        generation=GenerationService(),
        engine=EngineService(registry, local_reranker_enabled=rerank.available),
    )
