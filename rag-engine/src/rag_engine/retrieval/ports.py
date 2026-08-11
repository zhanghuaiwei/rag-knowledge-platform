"""授权检索流水线端口。"""

from typing import Protocol

from rag_engine.auth.models import RetrievalAccessContext
from rag_engine.retrieval.models import SearchQuery, SearchResult


class RetrievalPipeline(Protocol):
    """执行预过滤、融合召回和候选二次授权。"""

    def search(self, query: SearchQuery, context: RetrievalAccessContext) -> SearchResult:
        """只返回当前授权上下文允许的文档候选。"""
        ...
