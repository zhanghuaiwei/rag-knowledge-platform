"""授权检索、索引命中和 API 分页使用的内部模型。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from pydantic import BaseModel, Field


class SearchQuery(BaseModel):
    """由已验证授权上下文约束的索引查询。"""

    question: str = Field(min_length=1)
    tenant_id: int = Field(gt=0)
    allowed_document_ids: list[str] = Field(default_factory=list)
    top_k: int = Field(default=5, ge=1, le=100)
    fusion: str = Field(default="RRF", min_length=1)


class RetrievedChunk(BaseModel):
    """带版本、位置与相关性分数的候选分块。"""

    chunk_id: str = Field(min_length=1)
    document_id: str = Field(min_length=1)
    version_id: str = Field(min_length=1)
    text: str = Field(min_length=1)
    location: Any = None
    score: float = Field(ge=0)


class SearchResult(BaseModel):
    """带策略版本的授权检索结果。"""

    hits: list[RetrievedChunk] = Field(default_factory=list)
    total: int = Field(default=0, ge=0)
    policy_version: int = Field(ge=1)


@dataclass(frozen=True, slots=True)
class SearchPageSnapshot:
    """与 FastAPI/Pydantic DTO 解耦的应用层分页快照。"""

    items: tuple[dict[str, Any], ...]
    total: int
    page: int
    size: int
    has_more: bool
