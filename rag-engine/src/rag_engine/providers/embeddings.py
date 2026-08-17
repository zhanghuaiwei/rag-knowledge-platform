"""OpenAI 兼容 Embedding provider（通义 DashScope / 硅基流动 / 智谱等）。"""

from __future__ import annotations

import math

from openai import OpenAI

from rag_engine.indexing.ports import EmbeddingProvider


class OpenAiCompatibleEmbedding(EmbeddingProvider):
    """把文本批量编码为 L2 归一化向量的 OpenAI 兼容实现。

    统一走 ``POST {base_url}/embeddings``，仅依赖 OpenAI SDK 的 OpenAI 兼容端点：
    通义 DashScope（compatible-mode）、硅基流动、智谱等国内服务均为该形态。
    端口契约要求返回归一化向量，因此对输出做 L2 归一化——归一化后余弦相似度
    等价于点积，pgvector 的 ``<=>``（余弦距离）与 ``<#>``（负内积）结果一致，
    便于后续切换 distance_metric 而无需重训/重摄取。
    """

    def __init__(self, *, base_url: str, api_key: str, dimension: int) -> None:
        self._client = OpenAI(base_url=base_url, api_key=api_key or "EMPTY", timeout=60)
        self._dimension = dimension

    def embed(self, texts: list[str], *, model: str) -> list[list[float]]:
        """批量编码；text-embedding-v3 支持 ``dimensions`` 指定输出维度。"""
        # DashScope text-embedding-v3 支持 dimensions；其它兼容服务如不支持可去掉该参数
        # （向量维度须与 deploy/ddl/migrations/V0.6 的 vector(1024) 保持一致）。
        response = self._client.embeddings.create(
            input=texts,
            model=model,
            dimensions=self._dimension,
        )
        return [self._normalize(item.embedding) for item in response.data]

    def dimension(self, *, model: str) -> int:
        """返回配置的固定向量维度（与迁移/索引列一致）。"""
        del model
        return self._dimension

    @staticmethod
    def _normalize(vector: list[float]) -> list[float]:
        """L2 归一化：避免文本长度影响余弦相似度的绝对值。"""
        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0.0:
            return vector
        return [value / norm for value in vector]
