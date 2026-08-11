package com.ragkb.service.modules.admin.vo;

import java.time.Instant;

/**
 * Webhook 投递记录响应视图（对齐前端 Admin 契约）。
 */
public record WebhookDeliveryVo(
        long id,
        long subscriptionId,
        String eventId,
        String status,
        int attempts,
        Instant nextRetryAt,
        String responseSummary) {
}
