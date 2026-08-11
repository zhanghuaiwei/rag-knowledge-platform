package com.ragkb.service.modules.knowledge.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新知识库入参；status 用于归档等状态变更（产品契约所需，OpenAPI 草案未覆盖）。
 */
public record KbUpdateDto(
        @Size(max = 128) String name,
        @Size(max = 1024) String description,
        String visibility,
        Boolean requiresReview,
        Boolean ocrEnabled,
        String status) {
}
