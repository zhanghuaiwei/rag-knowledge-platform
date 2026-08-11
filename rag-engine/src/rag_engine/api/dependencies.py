"""FastAPI 对应用组合根中各功能服务的依赖解析。"""

from fastapi import Request

from rag_engine.config.settings import Settings
from rag_engine.container import ApplicationContainer
from rag_engine.engine.service import EngineService
from rag_engine.generation.service import GenerationService
from rag_engine.ingestion.service import IngestionService
from rag_engine.rerank.service import RerankService
from rag_engine.retrieval.service import RetrievalService


def get_container(request: Request) -> ApplicationContainer:
    """取得当前 FastAPI 应用持有的组合根。"""
    return request.app.state.container


def get_runtime_settings(request: Request) -> Settings:
    """取得当前进程使用的不可变运行配置。"""
    return get_container(request).settings


def get_ingestion_service(request: Request) -> IngestionService:
    """取得文档摄取功能服务。"""
    return get_container(request).ingestion


def get_retrieval_service(request: Request) -> RetrievalService:
    """取得全文检索功能服务。"""
    return get_container(request).retrieval


def get_rerank_service(request: Request) -> RerankService:
    """取得候选精排功能服务。"""
    return get_container(request).rerank


def get_generation_service(request: Request) -> GenerationService:
    """取得问答生成功能服务。"""
    return get_container(request).generation


def get_engine_service(request: Request) -> EngineService:
    """取得引擎健康与路由功能服务。"""
    return get_container(request).engine
