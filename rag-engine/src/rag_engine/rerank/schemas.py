"""精排 v0.1 HTTP DTO。"""

from pydantic import Field

from rag_engine.common.api import ApiModel


class RerankCandidate(ApiModel):
    """待精排的候选分块。"""

    chunk_id: str = Field(min_length=1)
    text: str = Field(min_length=1)


class RerankRequest(ApiModel):
    """精排请求。"""

    request_id: str = Field(min_length=1)
    query: str = Field(min_length=1)
    top_n: int = Field(default=10, ge=1, le=100)
    candidates: list[RerankCandidate]


class RerankItem(ApiModel):
    """单条精排结果。"""

    chunk_id: str
    score: float = Field(ge=0, le=1)


class RerankResult(ApiModel):
    """按相关性得分降序排列的精排结果。"""

    items: list[RerankItem]
