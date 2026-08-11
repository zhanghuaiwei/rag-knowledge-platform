"""全文搜索 v0.1 HTTP DTO。"""

from datetime import date

from pydantic import Field

from rag_engine.common.api import ApiModel


class SearchRequest(ApiModel):
    """全文搜索请求；最小实现只校验并返回稳定空分页。"""

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


class SearchItem(ApiModel):
    """带高亮片段和来源位置的搜索命中。"""

    document_id: int
    file_name: str
    kb_id: int
    page_no: int | None = None
    section_title: str | None = None
    file_ext: str | None = None
    snippet: str
    score: float = Field(ge=0)


class SearchResultPage(ApiModel):
    """全文搜索分页响应。"""

    items: list[SearchItem]
    total: int = Field(ge=0)
    page: int = Field(ge=1)
    size: int = Field(ge=1)
    has_more: bool
