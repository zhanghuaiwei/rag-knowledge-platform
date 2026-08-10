"""Provider 端口（结构化协议）：解析/OCR、向量化、精排、生成。

路由策略（sensitivity / region / purpose / budget / health）由调用方按
``05-技术选型 §3.7`` 校验，provider 自身不做降级授权；不合规 provider
不进入 fallback 集合。
"""
from __future__ import annotations

from collections.abc import Callable
from typing import Protocol

from rag_engine.domain.content import ContentBlock
from rag_engine.domain.retrieval import RetrievedChunk


class EmbeddingProvider(Protocol):
    """向量化：输入文本/分块，输出归一化向量。"""

    def embed(self, texts: list[str], *, model: str) -> list[list[float]]: ...

    def dimension(self, *, model: str) -> int: ...


class RerankerProvider(Protocol):
    """精排：对候选做交叉编码打分后按 top_n 截断。"""

    def rerank(
        self,
        query: str,
        candidates: list[RetrievedChunk],
        *,
        model: str,
        top_n: int,
    ) -> list[RetrievedChunk]: ...


class LlmProvider(Protocol):
    """生成：流式问答，必须支持逐 token 回调。"""

    def chat_stream(
        self,
        messages: list[dict[str, str]],
        *,
        model: str,
        on_token: Callable[[str], None],
    ) -> str: ...


class ParserProvider(Protocol):
    """文档解析：原始对象 → 结构化 ContentBlock 列表（含位置定位）。"""

    def parse(self, object_key: str, *, filename: str) -> list[ContentBlock]: ...
