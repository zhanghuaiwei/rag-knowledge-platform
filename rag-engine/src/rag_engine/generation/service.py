"""智能问答与 SSE 语义事件用例。"""

from rag_engine.generation.models import ChatEvent


class GenerationService:
    """未配置授权检索和 LLM 时返回明确 ``no_answer`` 的最小服务。"""

    def chat_events(
        self,
        *,
        request_id: str,
        session_id: int | None,
        kb_ids: list[int],
    ) -> tuple[ChatEvent, ChatEvent]:
        """生成合法的 ``meta -> final`` 降级事件序列。

        TODO(GenerationService.chat_events): 串联授权检索、候选复核、精排、低置信
        判定、LLM 流、引用和输出安全，并在客户端断开时取消下游调用。
        """
        return (
            ChatEvent(
                name="meta",
                sequence=1,
                payload={
                    "requestId": request_id,
                    "sessionId": session_id,
                    "kbIds": kb_ids,
                    "modelName": None,
                    "answerStatus": "no_answer",
                },
            ),
            ChatEvent(
                name="final",
                sequence=2,
                payload={
                    "requestId": request_id,
                    "answerStatus": "no_answer",
                    "confidence": 0.0,
                    "sources": [],
                    "suggestions": [],
                    "tokenIn": 0,
                    "tokenOut": 0,
                    "cost": 0.0,
                },
            ),
        )
