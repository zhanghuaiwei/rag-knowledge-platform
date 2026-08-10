"""存储与检索端口（结构化协议）。

索引是可重建派生数据（05-技术选型 ADR-3），数据库不保存向量；
引擎通过本端口读写 SearchIndex / ObjectStore / 密钥引用。
"""
from __future__ import annotations

from typing import Protocol

from rag_engine.domain.retrieval import RetrievedChunk, SearchQuery, SearchResult


class SearchIndex(Protocol):
    """统一搜索索引：BM25 + 向量 + 过滤 + 高亮 + alias 原子切换。"""

    def search(self, query: SearchQuery) -> SearchResult: ...

    def upsert_chunks(self, chunks: list[RetrievedChunk]) -> None: ...

    def delete_by_version(self, version_id: str) -> None: ...

    def switch_alias(self, alias: str, physical: str) -> None: ...


class ObjectStore(Protocol):
    """不可变对象存储：原始原文与派生预览。"""

    def get(self, object_key: str) -> bytes: ...

    def head(self, object_key: str) -> bool: ...


class SecretResolver(Protocol):
    """密钥解析：只返回引用对应的短期凭证，不落盘不记日志。"""

    def resolve(self, secret_ref: str) -> str: ...
