package com.ragkb.service.modules.admin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 暂停/恢复 Webhook 订阅入参。
 */
public record WebhookToggleDto(@NotNull Boolean paused) {
}
