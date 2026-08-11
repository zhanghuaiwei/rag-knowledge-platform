"""可替换精排模型 provider 端口。"""

from typing import Protocol

from rag_engine.retrieval.models import RetrievedChunk


class RerankerProvider(Protocol):
    """使用租户策略允许的模型对候选做批量精排。"""

    def rerank(
        self,
        query: str,
        candidates: list[RetrievedChunk],
        *,
        model: str,
        top_n: int,
    ) -> list[RetrievedChunk]:
        """按相关性降序返回不超过 ``top_n`` 个候选。"""
        ...
