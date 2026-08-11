"""向量化与派生搜索索引端口。"""

from typing import Protocol

from rag_engine.retrieval.models import RetrievedChunk, SearchQuery, SearchResult


class EmbeddingProvider(Protocol):
    """将文本批量转换为固定维度向量。"""

    def embed(self, texts: list[str], *, model: str) -> list[list[float]]:
        """返回与输入顺序一致的归一化向量。"""
        ...

    def dimension(self, *, model: str) -> int:
        """返回模型的固定向量维度。"""
        ...


class SearchIndex(Protocol):
    """BM25、向量、过滤、高亮和 alias 切换的统一端口。"""

    def search(self, query: SearchQuery) -> SearchResult:
        """执行带授权过滤条件的检索。"""
        ...

    def upsert_chunks(self, chunks: list[RetrievedChunk]) -> None:
        """幂等写入确定性 chunkId 对应的索引文档。"""
        ...

    def delete_by_version(self, version_id: str) -> int:
        """删除指定文档版本并返回删除数量。"""
        ...

    def switch_alias(self, alias: str, physical: str) -> None:
        """在构建校验通过后原子切换在线索引 alias。"""
        ...
