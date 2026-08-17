"""文档摄取前的内容安全功能包。

接入点：
- 摄取链路（``ingestion/service.py``）在解析前调用 ``ContentSafetyProvider.scan``；
- 生成链路（``generation/service.py``）可对用户 question 做提示词注入检测；
- 默认装配 ``NoopSafetyProvider``（永远放行），生产环境须在 ``container.py`` 替换。
"""

from rag_engine.safety.models import SafetyDecision, SafetyScanResult
from rag_engine.safety.noop_provider import NoopSafetyProvider
from rag_engine.safety.ports import ContentSafetyProvider

__all__ = [
    "ContentSafetyProvider",
    "NoopSafetyProvider",
    "SafetyDecision",
    "SafetyScanResult",
]
