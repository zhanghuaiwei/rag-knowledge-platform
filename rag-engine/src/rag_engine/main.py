"""rag-engine FastAPI 应用工厂。"""

from fastapi import FastAPI

from rag_engine.api.router import api_router
from rag_engine.config.settings import Settings, get_settings
from rag_engine.container import ApplicationContainer, build_container
from rag_engine.observability import configure_logging
from rag_engine.providers.registry import ProviderRegistry


def create_app(
    settings: Settings | None = None,
    *,
    providers: ProviderRegistry | None = None,
    container: ApplicationContainer | None = None,
) -> FastAPI:
    """创建可测试的 FastAPI 应用并装配各功能服务。"""
    runtime_settings = settings or get_settings()
    configure_logging(runtime_settings.log_level)
    application = FastAPI(
        title=runtime_settings.service_name,
        version=runtime_settings.service_version,
        root_path=runtime_settings.root_path,
        docs_url="/docs" if runtime_settings.docs_enabled else None,
        redoc_url="/redoc" if runtime_settings.docs_enabled else None,
        openapi_url="/openapi.json" if runtime_settings.docs_enabled else None,
    )
    application.state.container = container or build_container(
        runtime_settings,
        providers=providers,
    )
    application.include_router(api_router)
    return application


app = create_app()
