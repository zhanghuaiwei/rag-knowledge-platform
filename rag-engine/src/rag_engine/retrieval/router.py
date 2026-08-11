"""全文搜索 HTTP 路由。"""

from typing import Annotated

from fastapi import APIRouter, Depends

from rag_engine.api.dependencies import get_retrieval_service
from rag_engine.retrieval.schemas import SearchRequest, SearchResultPage
from rag_engine.retrieval.service import RetrievalService

router = APIRouter(prefix="/api/query", tags=["query"])
Retrieval = Annotated[RetrievalService, Depends(get_retrieval_service)]


@router.post("/search", response_model=SearchResultPage)
def search(request: SearchRequest, service: Retrieval) -> SearchResultPage:
    """执行全文搜索；当前未接授权索引，因此返回空分页。"""
    result = service.search(
        request_id=request.request_id,
        keyword=request.keyword,
        kb_ids=request.kb_ids,
        doc_id_whitelist=request.doc_id_whitelist,
        file_types=request.types,
        date_from=request.date_from,
        date_to=request.date_to,
        vector_fusion=request.vector_fusion,
        page=request.page,
        size=request.size,
    )
    return SearchResultPage(
        items=list(result.items),
        total=result.total,
        page=result.page,
        size=result.size,
        has_more=result.has_more,
    )
