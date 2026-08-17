"""智能问答与 SSE 语义事件用例。

最小 RAG 回答链路（2026-08-17）：
    query embedding → pgvector 检索 top_k（已按 kb 过滤 + 阈值滤除无证据）→
    三段式 Prompt（system 政策 / 参考资料 / 用户问题 + 历史）→ LLM 流式 token →
    sources → final。

SSE 事件序（对齐前端 /api-client/http/sse.ts 聚合契约）：
    meta → token* → sources → final
无证据时不调 LLM，直接 NO_ANSWER（不编造答案）；LLM 异常时 final=BLOCKED。
"""

from __future__ import annotations

from collections.abc import Iterator
from typing import TYPE_CHECKING

from rag_engine.generation.models import ChatEvent
from rag_engine.generation.ports import LlmProvider
from rag_engine.generation.schemas import ChatHistoryItem, KbConfig
from rag_engine.retrieval.models import SearchQuery

if TYPE_CHECKING:
    from rag_engine.indexing.ports import SearchIndex

# 回答状态（与前端 AnswerStatus 对齐：大写枚举，Java 侧透传）。
_ANSWERED = "ANSWERED"
_NO_ANSWER = "NO_ANSWER"
_BLOCKED = "BLOCKED"

# 三段式 Prompt 的系统政策：约束只依据参考资料作答，禁止编造。
_SYSTEM_PROMPT = (
    "你是企业知识库的问答助手。请只根据用户提供的「参考资料」回答问题，"
    "并在答案中标注引用来源编号（如 [1]、[2]）。若参考资料中没有答案，"
    "必须明确回答“资料中未找到相关信息”，不得编造或臆测资料之外的内容。"
)


class GenerationService:
    """真实 RAG 问答用例；缺 provider 时返回显式 no_answer（不假报）。"""

    def __init__(
        self,
        *,
        search_index: SearchIndex | None = None,
        llm: LlmProvider | None = None,
        llm_model: str = "",
    ) -> None:
        self._search_index = search_index
        self._llm = llm
        self._llm_model = llm_model

    def chat_events(
        self,
        *,
        request_id: str,
        session_id: int | None,
        kb_ids: list[int],
        question: str,
        history: list[ChatHistoryItem],
        kb_config: KbConfig,
        tenant_id: int,
    ) -> Iterator[ChatEvent]:
        """生成完整 SSE 事件序列（懒迭代，LLM 边生成边产 token）。"""
        if self._search_index is None or self._llm is None:
            yield from self._no_answer_events(request_id, session_id, kb_ids)
            return

        sequence = 1
        try:
            result = self._search_index.search(
                SearchQuery(
                    question=question,
                    tenant_id=tenant_id,
                    kb_ids=kb_ids,
                    top_k=kb_config.top_k,
                )
            )
            hits = result.hits
            max_score = max((hit.score for hit in hits), default=0.0)

            yield ChatEvent(
                name="meta",
                sequence=sequence,
                payload={
                    "requestId": request_id,
                    "sessionId": session_id,
                    "kbIds": kb_ids,
                    "modelName": self._llm_model,
                    "answerStatus": _ANSWERED if hits else _NO_ANSWER,
                },
            )

            if not hits:
                yield from self._final_no_answer(request_id, sequence + 1)
                return

            # 组装引用上下文与消息（三段式：system 政策 / 历史 / 资料 + 问题）。
            context = "\n\n".join(f"[{i + 1}] {hit.text}" for i, hit in enumerate(hits))
            messages = [{"role": "system", "content": _SYSTEM_PROMPT}]
            messages.extend({"role": item.role, "content": item.content} for item in history)
            messages.append(
                {
                    "role": "user",
                    "content": f"参考资料：\n{context}\n\n问题：{question}",
                }
            )

            # LLM 流式 token → SSE token 事件。
            parts: list[str] = []
            for token in self._llm.stream(messages, model=self._llm_model):
                parts.append(token)
                sequence += 1
                yield ChatEvent(
                    name="token",
                    sequence=sequence,
                    payload={"text": token},
                )

            sources = [
                {
                    "documentId": int(hit.document_id),
                    "versionId": int(hit.version_id),
                    "chunkId": hit.chunk_id,
                    "score": round(hit.score, 6),
                    "pageNo": (hit.location or {}).get("page_no"),
                    "sectionTitle": " > ".join((hit.location or {}).get("section_path", []))
                    or None,
                    # 文件名由检索 SQL LEFT JOIN document 提供（前端 ChatSource 必需）。
                    "fileName": hit.file_name or "",
                    # 引用片段：Java 侧用于 cited_text_sha256 留痕与前端定位。
                    "text": hit.text[:200],
                }
                for hit in hits
            ]

            sequence += 1
            yield ChatEvent(name="sources", sequence=sequence, payload={"sources": sources})

            sequence += 1
            yield ChatEvent(
                name="final",
                sequence=sequence,
                payload={
                    "requestId": request_id,
                    "answerStatus": _ANSWERED,
                    "confidence": round(max_score, 6),
                    "content": "".join(parts),
                    "sources": sources,
                    "suggestions": [],
                    "tokenIn": _estimate_tokens(context) + _estimate_tokens(question),
                    "tokenOut": _estimate_tokens("".join(parts)),
                    "cost": 0.0,
                },
            )
        except Exception:
            # LLM / 检索失败：不把内部错误泄露给前端，final=BLOCKED。
            yield ChatEvent(
                name="final",
                sequence=sequence + 1,
                payload={
                    "requestId": request_id,
                    "answerStatus": _BLOCKED,
                    "confidence": 0.0,
                    "content": "",
                    "sources": [],
                    "suggestions": [],
                    "tokenIn": 0,
                    "tokenOut": 0,
                    "cost": 0.0,
                },
            )

    # ------------------------------------------------------------------
    # 降级事件序列（缺 provider / 无证据）
    # ------------------------------------------------------------------

    def _no_answer_events(
        self, request_id: str, session_id: int | None, kb_ids: list[int]
    ) -> Iterator[ChatEvent]:
        yield ChatEvent(
            name="meta",
            sequence=1,
            payload={
                "requestId": request_id,
                "sessionId": session_id,
                "kbIds": kb_ids,
                "modelName": self._llm_model or None,
                "answerStatus": _NO_ANSWER,
            },
        )
        yield from self._final_no_answer(request_id, 2)

    def _final_no_answer(self, request_id: str, sequence: int) -> Iterator[ChatEvent]:
        yield ChatEvent(
            name="final",
            sequence=sequence,
            payload={
                "requestId": request_id,
                "answerStatus": _NO_ANSWER,
                "confidence": 0.0,
                "content": "资料中未找到相关信息",
                "sources": [],
                "suggestions": [],
                "tokenIn": 0,
                "tokenOut": 0,
                "cost": 0.0,
            },
        )


def _estimate_tokens(text: str) -> int:
    """粗略 token 估算（中文按字符、英文按空格分词）；精确用量待 usage 统计接入。"""
    if not text:
        return 0
    cjk_count = sum(1 for ch in text if "一" <= ch <= "鿿")
    return cjk_count + len(text.split())
