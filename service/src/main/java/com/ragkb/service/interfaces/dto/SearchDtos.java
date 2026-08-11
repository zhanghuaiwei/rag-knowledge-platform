package com.ragkb.service.interfaces.dto;

import java.time.Instant;

/**
 * 全文搜索域 DTO（对齐前端 SearchItem；snippet 为净化后的高亮片段）。
 */
public final class SearchDtos {

    private SearchDtos() {
    }

    public record SearchHit(
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

    public record SearchExcerpt(String excerpt, Object location) {
    }
}
