"""引擎能力健康和模型路由状态 HTTP 路由。"""

from typing import Annotated

from fastapi import APIRouter, Depends

from rag_engine.api.dependencies import get_engine_service
from rag_engine.engine.schemas import EngineHealth, EngineModel, RouteStatus, RouteStatusRequest
from rag_engine.engine.service import EngineService

router = APIRouter(prefix="/api/engine", tags=["engine"])
Engine = Annotated[EngineService, Depends(get_engine_service)]


@router.get("/health", response_model=EngineHealth)
def health(service: Engine) -> EngineHealth:
    """返回详细能力状态；与只表达进程存活的 ``/healthz`` 分离。"""
    return EngineHealth(
        status=service.health_status(),
        models=[
            EngineModel(name=name, available=available)
            for name, available in service.model_health()
        ],
    )


@router.post("/route-status", response_model=RouteStatus)
def route_status(request: RouteStatusRequest, service: Engine) -> RouteStatus:
    """查询指定 embedding 或 LLM 路由状态。"""
    available, latency_ms = service.route_status(
        route_type=request.route_type.value,
        model_name=request.model_name,
    )
    return RouteStatus(available=available, latency_ms=latency_ms)
