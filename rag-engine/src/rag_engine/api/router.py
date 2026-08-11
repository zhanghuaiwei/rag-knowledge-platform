"""内部 API 的统一路由装配入口。"""

from fastapi import APIRouter

from rag_engine.engine.router import router as engine_router
from rag_engine.generation.router import router as generation_router
from rag_engine.health.router import router as health_router
from rag_engine.ingestion.router import router as ingestion_router
from rag_engine.rerank.router import router as rerank_router
from rag_engine.retrieval.router import router as retrieval_router

api_router = APIRouter()
api_router.include_router(health_router)
api_router.include_router(ingestion_router)
api_router.include_router(retrieval_router)
api_router.include_router(rerank_router)
api_router.include_router(generation_router)
api_router.include_router(engine_router)
