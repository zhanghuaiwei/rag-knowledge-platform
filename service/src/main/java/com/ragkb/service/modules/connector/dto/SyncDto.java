package com.ragkb.service.modules.connector.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 触发同步入参。
 */
public record SyncDto(@NotBlank String syncType) {
}
