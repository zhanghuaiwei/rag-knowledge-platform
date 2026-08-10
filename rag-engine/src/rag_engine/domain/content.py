"""解析产物与内容块模型（定位元数据一致，与具体 provider 无关）。"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class SourceLocation(BaseModel):
    """内容定位：可表达 page / section / slide / sheet-cell / line。"""

    page_no: int | None = Field(default=None, ge=1)
    section_path: list[str] = Field(default_factory=list)
    block_type: str = "text"
    extra: dict[str, Any] = Field(default_factory=dict)


class ContentBlock(BaseModel):
    """统一解析输出单元：文本 + 定位 + 元数据。"""

    text: str = Field(min_length=1)
    location: SourceLocation = Field(default_factory=SourceLocation)
    metadata: dict[str, Any] = Field(default_factory=dict)
