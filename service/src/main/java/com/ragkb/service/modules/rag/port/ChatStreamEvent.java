package com.ragkb.service.modules.rag.port;

/**
 * 单条 SSE 事件（对齐 rag-engine 输出与前端 Chat 契约）。
 *
 * <p>{@code type} 取值：meta / token / sources / final（必要时含 error）；
 * {@code data} 为事件载荷（meta 含 requestId/sessionId/kbIds/answerStatus；
 * token 含 text；sources 含 sources 数组；final 含 answerStatus/content/sources/…）。
 */
public record ChatStreamEvent(String type, Object data) {
}
