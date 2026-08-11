"""引擎依赖健康和模型路由状态用例。"""

from rag_engine.providers.registry import ProviderRegistry


class EngineService:
    """基于已装配 provider 而非单纯环境变量报告 readiness。"""

    def __init__(self, registry: ProviderRegistry, *, local_reranker_enabled: bool) -> None:
        self._registry = registry
        self._local_reranker_enabled = local_reranker_enabled

    def model_health(self) -> list[tuple[str, bool]]:
        """返回契约要求的模型能力状态。"""
        return [
            ("minimal-lexical-reranker", self._local_reranker_enabled),
            ("embedding-provider", self._registry.embedding is not None),
            ("llm-provider", self._registry.llm is not None),
        ]

    def health_status(self) -> str:
        """主检索或生成 provider 未装配时返回 degraded。"""
        ready = (
            self._registry.embedding is not None
            and self._registry.search_index is not None
            and self._registry.llm is not None
        )
        return "ok" if ready else "degraded"

    def route_status(self, *, route_type: str, model_name: str) -> tuple[bool, int]:
        """查询当前进程是否装配指定类别的模型路由。

        TODO(EngineService.route_status): provider adapter 落地后接入有界健康缓存和真实
        延迟指标；不得在每个业务请求中同步调用外部探针。
        """
        del model_name
        available = {
            "embedding": self._registry.embedding is not None,
            "llm": self._registry.llm is not None,
        }.get(route_type, False)
        return available, 0
