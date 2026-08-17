"""大模型流式生成 provider 端口。"""

from collections.abc import Callable, Iterator
from typing import Protocol


class LlmProvider(Protocol):
    """基于已授权上下文执行流式回答生成。"""

    def stream(
        self,
        messages: list[dict[str, str]],
        *,
        model: str,
    ) -> Iterator[str]:
        """返回懒迭代器，逐 token 产出回答正文（SSE 生成路径使用，取消可传播）。"""
        ...

    def chat_stream(
        self,
        messages: list[dict[str, str]],
        *,
        model: str,
        on_token: Callable[[str], None],
    ) -> str:
        """逐 token 回调并返回完整回答；下游取消必须可传播。"""
        ...
