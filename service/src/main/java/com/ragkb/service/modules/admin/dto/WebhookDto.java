package com.ragkb.service.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建/更新 Webhook 订阅入参。
 */
public record WebhookDto(
        @NotBlank String name,
        @NotBlank String targetUrl,
        @NotEmpty List<@NotNull String> eventTypes) {
}
