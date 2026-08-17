"""多种 LLM provider 实现。

路由逻辑由 container.build_container 按 Settings.llm_provider_type 决定，本文件只提供实现：

* :class:`OpenAiCompatibleLlm` — OpenAI 兼容协议（通义 DashScope、硅基流动、DeepSeek、
  智谱、火山方舟等绝大多数服务）；支持 extra_headers 注入网关自定义头。
* :class:`AnthropicLlm` — Anthropic Messages API 原生实现；运行时延迟导入 SDK，
  若用户未安装 anthropic 包会抛出清晰错误提示。
* :class:`NoopLlm` — 永远返回空字符串的占位（开发/测试/降级），与 GenerationService
  的 fail-closed（无 LLM 发 NO_ANSWER）配合使用时实际不会被调用，但作为显式实现
  让 Settings.PROVIDER_TYPE=noop 能正常装配不出错。
"""

from __future__ import annotations

from collections.abc import Iterator

from rag_engine.generation.ports import LlmProvider


def _parse_extra_headers(raw: str) -> dict[str, str]:
    """解析 ``Header1:Val1,Header2:Val2`` 形式。空串返回空字典。"""
    if not raw:
        return {}
    headers: dict[str, str] = {}
    for piece in raw.split(","):
        item = piece.strip()
        if not item:
            continue
        if ":" not in item:
            raise ValueError(
                f"invalid extra_header entry: {item!r} (expected 'Name:Value')"
            )
        name, _, value = item.partition(":")
        headers[name.strip()] = value.strip()
    return headers


# ======================================================================
# Noop（占位，不给模型）
# ======================================================================


class NoopLlm(LlmProvider):
    """永远返回空 token 流；不发起任何网络请求。"""

    def __init__(self) -> None:
        pass

    def stream(self, messages: list[dict[str, str]], *, model: str) -> Iterator[str]:
        del messages, model
        # 完全不产出任何 token；调用方把空 parts 视为无回答，发 final(NO_ANSWER) 兜底
        return
        yield  # pragma: no cover - 让函数保持 generator 形态但立即 StopIteration

    def chat_stream(self, messages: list[dict[str, str]], *, model: str, on_token) -> str:
        del messages, model, on_token
        return ""


# ======================================================================
# OpenAI 兼容（绝大多数厂商）
# ======================================================================


class OpenAiCompatibleLlm(LlmProvider):
    """基于 OpenAI 兼容端点的流式 LLM 实现。

    ``stream`` 是懒迭代器：调用方（问答用例）边生成边逐 token 产出 SSE，
    不缓冲完整回答；客户端断开时迭代停止，天然传播取消。
    """

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        timeout_ms: int,
        extra_headers: str = "",
    ) -> None:
        # 延迟导入：测试/本地 minimal 路径不连真实模型时，不强制安装 SDK
        from openai import OpenAI

        self._client = OpenAI(
            base_url=base_url or None,
            api_key=api_key or "EMPTY",
            timeout=timeout_ms / 1000,
            default_headers=_parse_extra_headers(extra_headers) or None,
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


# ======================================================================
# Anthropic 原生（延迟导入 SDK）
# ======================================================================


class AnthropicLlm(LlmProvider):
    """Anthropic Messages API 原生实现（非 OpenAI 兼容）。

    使用延迟导入：未安装 ``anthropic`` 包时构造函数抛出清晰错误，不影响其它 provider。
    """

    def __init__(
        self,
        *,
        api_key: str,
        timeout_ms: int,
        model: str,  # 这里 model 仅用于校验是否传了；具体每次调用仍由调用方指定
        extra_headers: str = "",
    ) -> None:
        del model  # 预留给未来支持 client 侧默认 model 场景
        try:
            from anthropic import Anthropic  # type: ignore
        except ImportError as exc:  # pragma: no cover - 缺包时运行时提示
            raise RuntimeError(
                "使用 anthropic provider 需先安装 Anthropic SDK：`uv pip install anthropic>=0.40`"
                "，或改用 openai_compatible + anthropic 兼容代理端点。"
            ) from exc
        self._client = Anthropic(
            api_key=api_key or "EMPTY",
            timeout=timeout_ms / 1000,
            default_headers=_parse_extra_headers(extra_headers) or None,
        )
        self._timeout_ms = timeout_ms

    def stream(self, messages: list[dict[str, str]], *, model: str) -> Iterator[str]:
        """Anthropic 流式回答：client.messages.stream 逐个 text delta。"""
        # Anthropic Messages API 需要单独的 system 参数；从 messages 分离 system role。
        system_prompts: list[str] = [m["content"] for m in messages if m.get("role") == "system"]
        non_system = [m for m in messages if m.get("role") != "system"]
        with self._client.messages.stream(
            model=model,
            max_tokens=8192,
            system="\n".join(system_prompts) if system_prompts else None,
            messages=non_system,
        ) as stream:
            for text in stream.text_stream:
                if text:
                    yield text

    def chat_stream(self, messages: list[dict[str, str]], *, model: str, on_token) -> str:
        parts: list[str] = []
        for token in self.stream(messages, model=model):
            parts.append(token)
            on_token(token)
        return "".join(parts)
