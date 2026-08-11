"""本地确定性精排用例。"""

import re

from rag_engine.rerank.models import CandidateScore

_TOKEN_PATTERN = re.compile(r"[0-9A-Za-z_]+|[\u3400-\u4dbf\u4e00-\u9fff]")


class RerankService:
    """以词项覆盖率提供无模型环境可重复的联调精排。"""

    def __init__(self, *, enabled: bool = True) -> None:
        self._enabled = enabled

    @property
    def available(self) -> bool:
        """返回本地精排器是否由环境配置启用。"""
        return self._enabled

    def rerank(
        self,
        *,
        query: str,
        candidates: list[tuple[str, str]],
        top_n: int,
    ) -> list[CandidateScore]:
        """按查询词项覆盖率排序，同分保持输入顺序。

        TODO(RerankService.rerank): 接入 RerankerProvider 后增加批量限制、超时、熔断、
        指标和 provider 错误映射；本地算法不能作为生产相关性质量结论。
        """
        if not self._enabled:
            return []

        query_tokens = self._tokenize(query)
        scored: list[tuple[int, CandidateScore]] = []
        for index, (chunk_id, text) in enumerate(candidates):
            candidate_tokens = self._tokenize(text)
            score = (
                len(query_tokens & candidate_tokens) / len(query_tokens) if query_tokens else 0.0
            )
            scored.append((index, CandidateScore(chunk_id=chunk_id, score=round(score, 6))))

        scored.sort(key=lambda item: (-item[1].score, item[0]))
        return [item for _, item in scored[:top_n]]

    @staticmethod
    def _tokenize(text: str) -> set[str]:
        """执行轻量、确定性的中英文词项切分。"""
        return {token.casefold() for token in _TOKEN_PATTERN.findall(text)}
