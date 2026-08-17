"""智能问答 SSE HTTP 路由。"""

from __future__ import annotations

import json
from collections.abc import Iterator
from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from rag_engine.api.dependencies import get_generation_service
from rag_engine.generation.models import ChatEvent
from rag_engine.generation.schemas import QueryChatRequest
from rag_engine.generation.service import GenerationService

router = APIRouter(prefix="/api/query", tags=["chat"])
Generator = Annotated[GenerationService, Depends(get_generation_service)]


def encode_sse(event: ChatEvent) -> str:
    """按单行 JSON 编码 SSE，避免换行破坏事件边界。"""
    data = json.dumps(event.payload, ensure_ascii=False, separators=(",", ":"))
    return f"id: {event.sequence}\nevent: {event.name}\ndata: {data}\n\n"


@router.post("/chat", response_class=StreamingResponse)
def chat(request: QueryChatRequest, service: Generator) -> StreamingResponse:
    """返回 ``meta -> final(no_answer)`` 的最小 SSE 流。"""
    events = service.chat_events(
        request_id=request.request_id,
        session_id=request.session_id,
        kb_ids=request.kb_ids,
        question=request.question,
        history=request.history,
        kb_config=request.kb_config,
        tenant_id=request.tenant_id,
    )

    def event_stream() -> Iterator[str]:
        for event in events:
            yield encode_sse(event)

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
