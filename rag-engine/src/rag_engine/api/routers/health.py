"""运维探针路由：进程存活检查。详细依赖健康见 06-架构方案 §6。"""

from fastapi import APIRouter

router = APIRouter(tags=["ops"])


@router.get("/healthz")
def healthz() -> dict[str, str]:
    """探针端点：进程存活。"""
    return {"status": "ok", "service": "rag-engine", "phase": "scaffold"}
