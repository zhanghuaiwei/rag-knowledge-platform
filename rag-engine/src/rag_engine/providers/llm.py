"""OpenAI 兼容 LLM provider（流式生成，通义 DashScope / 硅基流动 / 智谱等）。"""

from __future__ import annotations

from collections.abc import Iterator

from openai import OpenAI

from rag_engine.generation.ports import LlmProvider


class OpenAiCompatibleLlm(LlmProvider):
    """基于 OpenAI 兼容端点的流式 LLM 实现。

    ``stream`` 是懒迭代器：调用方（问答用例）边生成边逐 token 产出 SSE，
    不缓冲完整回答；客户端断开时迭代停止，天然传播取消。
    """

    def __init__(self, *, base_url: str, api_key: str, timeout_ms: int) -> None:
        self._client = OpenAI(
            base_url=base_url,
            api_key=api_key or "EMPTY",
            timeout=timeout_ms / 1000,
        )
        self._timeout_ms = timeout_ms

    def stream(self, messages: list[dict[str, str]], *, model: str) -> Iterator[str]:
        """逐 token 产出回答正文；调用方负责按 LLM 失败边界重试/降级。"""
        stream = self._client.chat.completions.create(
            model=model,
            messages=messages,
            stream=True,
        )
        for chunk in stream:
            if chunk.choices and chunk.choices[0].delta and chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content

    def chat_stream(self, messages: list[dict[str, str]], *, model: str, on_token) -> str:
        """端口兼容包装：把 ``stream`` 的 token 逐段回调并返回完整回答。"""
        parts: list[str] = []
        for token in self.stream(messages, model=model):
            parts.append(token)
            on_token(token)
        return "".join(parts)
