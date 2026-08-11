package com.ragkb.service.modules.conversation.vo;

/**
 * 单条 SSE 事件响应视图（OpenAPI 约定：meta / token / sources / final / error）。
 */
public record ChatEventVo(String type, Object data) {
}
