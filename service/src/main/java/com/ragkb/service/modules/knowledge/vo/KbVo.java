package com.ragkb.service.modules.knowledge.vo;

import java.time.Instant;
import java.util.List;

/**
 * 知识库响应视图（对齐前端 Kb 契约；OpenAPI 草案缺 role/members/documentCount 等 UI 必需字段，
 * 此处按产品契约补全）。
 */
public record KbVo(
        long id,
        String name,
        String description,
        String visibility,
        String status,
        String role,
        long documentCount,
        long chunkCount,
        String dataRegion,
        String indexProfileName,
        boolean requiresReview,
        boolean ocrEnabled,
        Instant createdAt,
        Instant updatedAt,
        List<KbMemberVo> members) {
}
