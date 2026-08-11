package com.ragkb.service.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 创建 API Key 入参。
 */
public record ApiKeyCreateDto(
        @NotBlank @Size(max = 128) String name,
        @NotEmpty List<String> scopes,
        List<Long> allowedKbIds,
        Instant expiresAt) {
}
