"""文件类型、恶意软件、DLP 与提示词注入扫描端口。"""

from typing import Protocol

from rag_engine.safety.models import SafetyScanResult


class ContentSafetyProvider(Protocol):
    """对隔离区对象执行安全扫描。"""

    def scan(self, object_key: str) -> SafetyScanResult:
        """返回允许或阻断结果；扫描异常不得默认放行。"""
        ...
