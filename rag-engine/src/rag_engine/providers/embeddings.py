"""多种 Embedding provider 实现。

路由逻辑由 container.build_container 按 Settings.embedding_provider_type 决定，本文件只提供实现：

* :class:`OpenAiCompatibleEmbedding` — OpenAI 兼容协议批量编码（通义 DashScope、
  硅基流动、智谱等）；支持 dimensions 参数（DashScope text-embedding-v3 支持）+
  extra_headers 网关自定义头；输出 L2 归一化向量。
* :class:`DashScopeNativeEmbedding` ——（占位，预留未来接阿里云 dashscope 原生 batch 接口
  以获得更高吞吐），当前与 OpenAiCompatibleEmbedding 行为一致，统一走兼容模式。
* :class:`NoopEmbedding` —— 返回零向量（长度为配置的 dimension），用于测试或 minimal
  降级路径，保证 PgVectorSearchIndex 在无 embedding provider 时调用依然能构造（但
  fail-closed 语义下 container 会把 search_index 置 None，所以 NoopEmbedding 不会
  被真实用于向量写入或检索）。
"""

from __future__ import annotations

import math

from rag_engine.indexing.ports import EmbeddingProvider
from rag_engine.providers.llm import _parse_extra_headers  # 复用同一份 header 解析器


class NoopEmbedding(EmbeddingProvider):
    """返回零向量的占位实现，永远成功，不发网络请求。"""

    def __init__(self, *, dimension: int) -> None:
        if dimension <= 0:
            raise ValueError("NoopEmbedding 必须显式指定 dimension>0")
        self._dimension = dimension

    def embed(self, texts: list[str], *, model: str) -> list[list[float]]:
        del model
        zero = [0.0 for _ in range(self._dimension)]
        return [zero[:] for _ in texts]

    def dimension(self, *, model: str) -> int:
        del model
        return self._dimension


class OpenAiCompatibleEmbedding(EmbeddingProvider):
    """把文本批量编码为 L2 归一化向量的 OpenAI 兼容实现。

    统一走 ``POST {base_url}/embeddings``，仅依赖 OpenAI SDK 的 OpenAI 兼容端点：
    通义 DashScope（compatible-mode）、硅基流动、智谱等国内服务均为该形态。
    端口契约要求返回归一化向量，因此对输出做 L2 归一化——归一化后余弦相似度
    等价于点积，pgvector 的 ``<=>``（余弦距离）与 ``<#>``（负内积）结果一致，
    便于后续切换 distance_metric 而无需重训/重摄取。
    """

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        dimension: int,
        extra_headers: str = "",
    ) -> None:
        # 延迟导入：测试/本地 minimal 路径不连真实模型时，不强制安装 SDK
        from openai import OpenAI

        self._client = OpenAI(
            base_url=base_url or None,
            api_key=api_key or "EMPTY",
            timeout=60,
            default_headers=_parse_extra_headers(extra_headers) or None,
        )
        if dimension <= 0:
            raise ValueError("OpenAiCompatibleEmbedding 必须显式指定 dimension>0")
        self._dimension = dimension

    def embed(self, texts: list[str], *, model: str) -> list[list[float]]:
        """批量编码；text-embedding-v3 支持 ``dimensions`` 指定输出维度。"""
        # DashScope text-embedding-v3 支持 dimensions；其它兼容服务（如 bge-m3）不支持
        # 会报错，调用方应通过 embedding_provider_type+预设来正确选择模型与维度对。
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
