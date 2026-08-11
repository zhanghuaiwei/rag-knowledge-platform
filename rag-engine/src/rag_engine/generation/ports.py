"""大模型流式生成 provider 端口。"""

from collections.abc import Callable
from typing import Protocol


class LlmProvider(Protocol):
    """基于已授权上下文执行流式回答生成。"""

    def chat_stream(
        self,
        messages: list[dict[str, str]],
        *,
        model: str,
        on_token: Callable[[str], None],
    ) -> str:
        """逐 token 回调并返回完整回答；下游取消必须可传播。"""
        ...
