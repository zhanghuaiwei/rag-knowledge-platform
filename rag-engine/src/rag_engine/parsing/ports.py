"""文档解析和 OCR provider 端口。"""

from typing import Protocol

from rag_engine.parsing.models import ContentBlock


class ParserProvider(Protocol):
    """把安全扫描通过的不可变对象解析为结构化内容块。"""

    def parse(self, object_key: str, *, filename: str) -> list[ContentBlock]:
        """解析对象并保留可用于引用的来源定位。"""
        ...
