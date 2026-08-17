"""基于 langchain 的文档解析器（文本格式：md / txt / csv / html）。

使用说明：
* 分块阶段真正使用 langchain —— ``RecursiveCharacterTextSplitter``（中英文混合的
  递归分隔符）在摄取流水线里完成 chunking；
* 本解析器只负责「把对象字节 → 结构化正文块」：文本格式直接用标准库解码，
  不引入 pdf/office 的重型 loader 依赖（MVP 第三轮之后可换
  langchain_community 的 PyPDFLoader / Docx2txtLoader 等，见模块注释迭代点）。
"""

from __future__ import annotations

import csv
import html.parser
import io

from rag_engine.parsing.models import ContentBlock, SourceLocation
from rag_engine.parsing.ports import ParserProvider
from rag_engine.providers.ports import ObjectStore

# 当前支持解析的文本扩展名（与前端 accept 的子集，MVP 先覆盖 Markdown/TXT/CSV/HTML）。
_TEXT_EXTS = {"md", "txt", "markdown", "csv", "html", "htm"}


class _HtmlTextExtractor(html.parser.HTMLParser):
    """极简 HTML → 纯文本抽取器（stdlib html.parser，避免额外依赖）。"""

    def __init__(self) -> None:
        super().__init__()
        self._parts: list[str] = []

    def handle_data(self, data: str) -> None:
        self._parts.append(data)

    def text(self) -> str:
        return "".join(self._parts)


class LangChainParser(ParserProvider):
    """把对象存储里的文本对象解析为统一 ContentBlock（供后续 langchain 分块）。"""

    def __init__(self, object_store: ObjectStore) -> None:
        self._object_store = object_store

    def parse(self, object_key: str, *, filename: str) -> list[ContentBlock]:
        ext = _extension_of(filename)
        if ext not in _TEXT_EXTS:
            raise ValueError(
                f"暂不支持解析 .{ext} 格式；最小 MVP 仅支持 md/txt/csv/html"
                f"（PDF/Office 需接入专用 langchain loader）"
            )
        raw = self._object_store.get(object_key)
        return [ContentBlock(text=self._decode(raw, ext), location=SourceLocation(block_type=ext))]

    def _decode(self, raw: bytes, ext: str) -> str:
        """按格式把字节转为纯文本。"""
        if ext in {"csv"}:
            return _read_csv(raw)
        if ext in {"html", "htm"}:
            parser = _HtmlTextExtractor()
            parser.feed(_decode_utf8(raw))
            return parser.text()
        return _decode_utf8(raw)


def _decode_utf8(raw: bytes) -> str:
    """UTF-8 容错解码；含 BOM 时先剥掉。"""
    text = raw.decode("utf-8-sig", errors="replace")
    return text.replace("\r\n", "\n").replace("\r", "\n")


def _read_csv(raw: bytes) -> str:
    """CSV → 每行拼接为 'col1, col2, ...' 的正文（保证分块不丢列语义）。"""
    reader = csv.reader(io.StringIO(_decode_utf8(raw)))
    lines: list[str] = []
    for row in reader:
        if row:
            lines.append(", ".join(cell.strip() for cell in row))
    return "\n".join(lines)


def _extension_of(filename: str) -> str:
    dot = filename.rfind(".")
    return filename[dot + 1 :].lower() if 0 <= dot < len(filename) - 1 else ""
