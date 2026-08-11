package com.ragkb.service.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增/更新知识库成员入参。
 */
public record KbMemberDto(
        @NotNull Long userId,
        @NotBlank String role) {
}
