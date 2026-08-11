package com.ragkb.service.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库入参（对齐前端新建向导字段）。
 */
public record KbCreateDto(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1024) String description,
        String visibility,
        String domain,
        String sensitivity,
        String retention,
        String dataRegion,
        String modelPolicy,
        Boolean requiresReview,
        Boolean ocrEnabled) {
}
