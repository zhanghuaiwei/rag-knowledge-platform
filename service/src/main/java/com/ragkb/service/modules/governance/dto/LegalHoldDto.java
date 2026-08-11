package com.ragkb.service.modules.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建法律保全入参。
 */
public record LegalHoldDto(
        @NotBlank String name,
        @NotBlank String reason,
        @NotEmpty List<@NotNull Long> documentIds) {
}
