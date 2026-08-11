"""进程存活探针 HTTP 路由。"""

from typing import Annotated

from fastapi import APIRouter, Depends

from rag_engine.api.dependencies import get_runtime_settings
from rag_engine.config.settings import Settings

router = APIRouter(tags=["ops"])
RuntimeSettings = Annotated[Settings, Depends(get_runtime_settings)]


@router.get("/healthz")
def healthz(settings: RuntimeSettings) -> dict[str, str]:
    """仅表达 HTTP 进程存活，不代表索引或模型 provider 已就绪。"""
    return {
        "status": "ok",
        "service": settings.service_name,
        "phase": settings.phase,
    }
