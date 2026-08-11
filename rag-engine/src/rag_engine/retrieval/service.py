"""全文搜索应用用例。"""

from datetime import date

from rag_engine.retrieval.models import SearchPageSnapshot


class RetrievalService:
    """未配置授权索引时返回稳定空结果的最小检索服务。"""

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
        """返回确定性空分页，避免泄露或伪造索引内容。

        TODO(RetrievalService.search): v0.2 契约冻结后验证签名
        RetrievalAccessContext，构造 tenant/KB/document/state 过滤，调用 SearchIndex，
        并在返回前对候选文档二次授权。
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
        return SearchPageSnapshot(items=(), total=0, page=page, size=size, has_more=False)
