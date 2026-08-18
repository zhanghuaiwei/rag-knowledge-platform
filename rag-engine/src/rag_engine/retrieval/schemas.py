"""全文搜索 v0.1 HTTP DTO。"""

from datetime import date

from pydantic import Field

from rag_engine.common.api import ApiModel


class SearchRequest(ApiModel):
    """全文搜索请求；未装配授权索引（minimal）时返回稳定空分页。"""

    request_id: str = Field(min_length=1)
    keyword: str = Field(min_length=1)
    kb_ids: list[int] = Field(default_factory=list)
    doc_id_whitelist: list[int] = Field(default_factory=list)
    types: list[str] = Field(default_factory=list)
    date_from: date | None = None
    date_to: date | None = None
    page: int = Field(default=1, ge=1)
    size: int = Field(default=20, ge=1, le=100)
    vector_fusion: bool = False
    # 2026-08-17 新增：租户过滤（Java 侧从 JWT 解析后透传；默认租户 1 兼容单租户开发，
    # 与 QueryChatRequest 同一约定）。多租户红线：检索 SQL 必须携带 tenant_id。
    tenant_id: int = Field(default=1, gt=0)


class SearchItem(ApiModel):
    """带高亮片段和来源位置的搜索命中。

    ``chunk_id`` 兼作命中 id（hitId），供摘录端点按 id 回查片段正文；
    ``updated_at`` 取 document.updated_at（ISO 字符串），前端展示「更新于」用。
    """

    document_id: int
    file_name: str
    kb_id: int
    page_no: int | None = None
    section_title: str | None = None
    file_ext: str | None = None
    snippet: str
    score: float = Field(ge=0)
    chunk_id: str | None = None
    updated_at: str | None = None


class SearchHitDetail(ApiModel):
    """单个命中的片段正文（摘录端点响应）。"""

    chunk_id: str
    document_id: int
    version_id: int
    file_name: str = ""
    file_ext: str | None = None
    page_no: int | None = None
    section_title: str | None = None
    text: str


class SearchResultPage(ApiModel):
    """全文搜索分页响应。"""

    items: list[SearchItem]
    total: int = Field(ge=0)
    page: int = Field(ge=1)
    size: int = Field(ge=1)
    has_more: bool
