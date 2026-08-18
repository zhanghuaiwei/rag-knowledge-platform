"""全文搜索 HTTP 路由。"""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query

from rag_engine.api.dependencies import get_retrieval_service
from rag_engine.retrieval.schemas import (
    SearchHitDetail,
    SearchItem,
    SearchRequest,
    SearchResultPage,
)
from rag_engine.retrieval.service import RetrievalService

router = APIRouter(prefix="/api/query", tags=["search"])
Retrieval = Annotated[RetrievalService, Depends(get_retrieval_service)]


@router.post("/search", response_model=SearchResultPage)
def search(request: SearchRequest, service: Retrieval) -> SearchResultPage:
    """执行全文搜索；未装配授权索引（minimal）时返回稳定空分页。"""
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
        # 租户上下文由 Java 侧从 JWT 解析后透传（多租户红线：贯穿索引查询）。
        tenant_id=request.tenant_id,
    )
    return SearchResultPage(
        items=[SearchItem.model_validate(item) for item in result.items],
        total=result.total,
        page=result.page,
        size=result.size,
        has_more=result.has_more,
    )


@router.get("/hits/{chunk_id}", response_model=SearchHitDetail)
def get_hit(
    chunk_id: str,
    service: Retrieval,
    # GET 无请求体，租户上下文经 query 参数透传（内部 API，与 ingestion 同一信任域）。
    tenant_id: Annotated[int, Query(gt=0)] = 1,
) -> SearchHitDetail:
    """按命中 id（chunk_id）回查片段正文（搜索摘录）。

    跨租户或不存在的 chunk_id 统一按 404 拒绝（deny-by-default，不泄露存在性）。
    """
    row = service.get_hit(chunk_id=chunk_id, tenant_id=tenant_id)
    if row is None:
        raise HTTPException(status_code=404, detail="search hit not found")
    return SearchHitDetail(
        chunk_id=row.chunk_id,
        document_id=row.document_id,
        version_id=row.version_id,
        file_name=row.file_name,
        file_ext=row.file_ext,
        page_no=row.page_no,
        section_title=" > ".join(row.section_path) or None,
        text=row.text,
    )
