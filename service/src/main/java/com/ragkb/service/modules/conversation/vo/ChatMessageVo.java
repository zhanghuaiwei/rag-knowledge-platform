package com.ragkb.service.modules.conversation.vo;

import java.time.Instant;
import java.util.List;

/**
 * 问答消息响应视图（对齐前端 Chat 契约）。
 */
public record ChatMessageVo(
        long id,
        long sessionId,
        long seq,
        String role,
        String content,
        String answerStatus,
        Double confidence,
        int feedback,
        long tokenIn,
        long tokenOut,
        String modelName,
        List<ChatSourceVo> sources,
        List<String> suggestions,
        Instant createdAt) {
}
