package com.ragkb.service.modules.conversation.vo;

import java.time.Instant;

/**
 * 全文搜索命中响应视图（对齐前端 SearchItem；snippet 为净化后的高亮片段）。
 */
public record SearchHitVo(
        long documentId,
        long kbId,
        String fileName,
        String fileExt,
        int pageNo,
        String sectionTitle,
        String snippet,
        double score,
        Instant updatedAt) {
}
