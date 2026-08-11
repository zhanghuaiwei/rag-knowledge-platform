package com.ragkb.service.modules.conversation.vo;

import java.time.Instant;
import java.util.List;

/**
 * 智能问答会话响应视图（对齐前端 Chat 契约）。
 */
public record ChatSessionVo(
        long id,
        String title,
        String status,
        List<Long> kbIds,
        long messageCount,
        Instant createdAt,
        Instant updatedAt) {
}
