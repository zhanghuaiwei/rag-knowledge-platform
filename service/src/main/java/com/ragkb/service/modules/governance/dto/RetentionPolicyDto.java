package com.ragkb.service.modules.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建保留策略入参。
 */
public record RetentionPolicyDto(
        @NotBlank String name,
        @NotBlank String appliesTo,
        @NotNull Integer durationMonths,
        @NotBlank String action) {
}
