"""候选精排 HTTP 路由。"""

from typing import Annotated

from fastapi import APIRouter, Depends

from rag_engine.api.dependencies import get_rerank_service
from rag_engine.rerank.schemas import RerankItem, RerankRequest, RerankResult
from rag_engine.rerank.service import RerankService

router = APIRouter(prefix="/api/query", tags=["query"])
Reranker = Annotated[RerankService, Depends(get_rerank_service)]


@router.post("/rerank", response_model=RerankResult)
def rerank(request: RerankRequest, service: Reranker) -> RerankResult:
    """对候选做确定性本地精排，并返回不超过 topN 条结果。"""
    scored = service.rerank(
        query=request.query,
        candidates=[(item.chunk_id, item.text) for item in request.candidates],
        top_n=request.top_n,
    )
    return RerankResult(
        items=[RerankItem(chunk_id=item.chunk_id, score=item.score) for item in scored]
    )
