"""摄取流水线端口（抽象）：原始对象 → 安全 → 解析 → 分块 → 向量化 → 索引。

同一 ``idempotency_key`` 只产生一份结果；失败可重放。
"""
from __future__ import annotations

from abc import ABC, abstractmethod

from rag_engine.domain.content import ContentBlock
from rag_engine.domain.retrieval import RetrievedChunk


class IngestPipeline(ABC):
    """摄取编排端口：实现由用户完成，本文件只定义流水线形状。"""

    @abstractmethod
    def parse(self, object_key: str) -> list[ContentBlock]:
        """解析原始对象为结构化内容块（安全扫描通过后）。"""

    @abstractmethod
    def chunk(self, blocks: list[ContentBlock]) -> list[RetrievedChunk]:
        """按不可变 index profile 分块。"""

    @abstractmethod
    def index(self, chunks: list[RetrievedChunk], *, idempotency_key: str) -> int:
        """向量化并写入 SearchIndex，返回写入 chunk 数。"""
