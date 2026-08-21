"""rag-engine FastAPI 应用工厂."""

import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from rag_engine.api.router import api_router
from rag_engine.config.settings import Settings, get_settings
from rag_engine.container import ApplicationContainer, build_container
from rag_engine.observability import configure_logging
from rag_engine.providers.registry import ProviderRegistry

logger = logging.getLogger(__name__)


def create_app(
    settings: Settings | None = None,
    *,
    providers: ProviderRegistry | None = None,
    container: ApplicationContainer | None = None,
) -> FastAPI:
    """创建可测试的 FastAPI 应用并装配各功能服务."""
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

    @application.middleware("http")
    async def log_chat_request(request: Request, call_next):
        """临时调试：打印 /api/query/chat 的请求体和 422 响应体."""
        if request.url.path == "/api/query/chat" and request.method == "POST":
            body = await request.body()
            logger.warning("chat request body: %s", body.decode("utf-8", errors="replace"))
            response = await call_next(request)
            if response.status_code == 422:
                # 422 的响应体需要读出来再放回去
                async for chunk in response.body_iterator:
                    logger.warning("chat 422 response: %s", chunk.decode("utf-8", errors="replace"))
                    break
            return response
        return await call_next(request)

    return application


app = create_app()
