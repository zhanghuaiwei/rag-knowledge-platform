"""问答流水线端口（抽象）：授权检索 → 融合 → 精排 → 生成(SSE)。

仅接受已认证服务与有效 ``RetrievalAccessContext``；生成阶段保持
安全输出过滤与引用归属（03-详细设计 §8/§9）。
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Callable

from rag_engine.domain.context import RetrievalAccessContext
from rag_engine.domain.retrieval import SearchQuery, SearchResult


class QueryPipeline(ABC):
    """问答编排端口：实现由用户完成，本文件只定义流水线形状。"""

    @abstractmethod
    def search(
        self, query: SearchQuery, ctx: RetrievalAccessContext
    ) -> SearchResult:
        """授权检索（allowed_document_ids 预过滤，候选进入 LLM 前二次授权）。"""

    @abstractmethod
    def generate(
        self,
        query: SearchQuery,
        ctx: RetrievalAccessContext,
        sources: SearchResult,
        on_token: Callable[[str], None],
    ) -> str:
        """基于检索来源生成答案并逐 token 回调；返回完整回答文本。"""
