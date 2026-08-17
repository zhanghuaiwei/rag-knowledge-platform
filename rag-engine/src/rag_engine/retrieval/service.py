"""全文搜索应用用例。"""

from __future__ import annotations

from datetime import date
from typing import TYPE_CHECKING

from rag_engine.retrieval.models import SearchPageSnapshot

if TYPE_CHECKING:
    from rag_engine.indexing.ports import SearchIndex


class RetrievalService:
    """全文搜索用例；未装配授权索引时返回稳定空结果（不泄露索引内容）。"""

    def __init__(self, *, search_index: SearchIndex | None = None) -> None:
        self._search_index = search_index

    def search(
        self,
        *,
        request_id: str,
        keyword: str,
        kb_ids: list[int],
        doc_id_whitelist: list[int],
        file_types: list[str],
        date_from: date | None,
        date_to: date | None,
        vector_fusion: bool,
        page: int,
        size: int,
    ) -> SearchPageSnapshot:
        """返回分页命中；未装配索引时返回确定性空分页。

        TODO(RetrievalService.search): v0.2 契约冻结后验证授权上下文并二次授权候选文档。
        最小闭环先走问答（chat）路径，全文搜索端点保持空结果。
        """
        del (
            request_id,
            keyword,
            kb_ids,
            doc_id_whitelist,
            file_types,
            date_from,
            date_to,
            vector_fusion,
        )
        if self._search_index is None:
            return SearchPageSnapshot(items=(), total=0, page=page, size=size, has_more=False)
        return SearchPageSnapshot(items=(), total=0, page=page, size=size, has_more=False)
