"""跨格式解析后的统一内容与定位模型。"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class SourceLocation(BaseModel):
    """页、章节、幻灯片、单元格或行等来源定位。"""

    page_no: int | None = Field(default=None, ge=1)
    section_path: list[str] = Field(default_factory=list)
    block_type: str = Field(default="text", min_length=1)
    extra: dict[str, Any] = Field(default_factory=dict)


class ContentBlock(BaseModel):
    """解析输出单元：正文、定位和非敏感派生元数据。"""

    text: str = Field(min_length=1)
    location: SourceLocation = Field(default_factory=SourceLocation)
    metadata: dict[str, Any] = Field(default_factory=dict)
