"""检索与问答模型（授权过滤、融合、精排、引用的数据结构）。"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class SearchQuery(BaseModel):
    """授权检索请求：allowed_document_ids 由 RetrievalAccessContext 派生。"""

    question: str = Field(min_length=1)
    tenant_id: int = Field(gt=0)
    allowed_document_ids: list[str] = Field(default_factory=list)
    top_k: int = Field(default=5, ge=1, le=100)
    fusion: str = Field(default="RRF")


class RetrievedChunk(BaseModel):
    """命中的分块（含来源定位与分数，供生成与引用渲染）。"""

    chunk_id: str
    document_id: str
    version_id: str
    text: str
    location: Any = None
    score: float = Field(ge=0)


class SearchResult(BaseModel):
    """检索结果集合。"""

    hits: list[RetrievedChunk] = Field(default_factory=list)
    total: int = 0
    policy_version: int = Field(ge=1)
