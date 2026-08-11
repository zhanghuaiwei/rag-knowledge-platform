"""问答生成流的语义事件模型。"""

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True, slots=True)
class ChatEvent:
    """由 API 层编码为 SSE 的应用语义事件。"""

    name: str
    sequence: int
    payload: dict[str, Any]
