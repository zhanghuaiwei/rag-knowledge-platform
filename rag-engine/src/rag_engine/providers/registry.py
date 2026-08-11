"""外部 provider 的显式装配注册表。"""

from dataclasses import dataclass

from rag_engine.generation.ports import LlmProvider
from rag_engine.indexing.ports import EmbeddingProvider, SearchIndex
from rag_engine.parsing.ports import ParserProvider
from rag_engine.providers.ports import ObjectStore, SecretResolver
from rag_engine.rerank.ports import RerankerProvider
from rag_engine.safety.ports import ContentSafetyProvider


@dataclass(frozen=True, slots=True)
class ProviderRegistry:
    """应用已实际装配的 provider 集合。

    环境变量只描述运行配置；只有实例进入本注册表后，健康检查才会宣告能力可用。
    这可避免“配置了地址但 adapter 不存在”被误报成 readiness 成功。
    """

    object_store: ObjectStore | None = None
    secret_resolver: SecretResolver | None = None
    content_safety: ContentSafetyProvider | None = None
    parser: ParserProvider | None = None
    embedding: EmbeddingProvider | None = None
    search_index: SearchIndex | None = None
    reranker: RerankerProvider | None = None
    llm: LlmProvider | None = None

    @classmethod
    def minimal(cls) -> "ProviderRegistry":
        """返回不连接任何外部基础设施的安全默认注册表。"""
        return cls()
