package com.ragkb.service.modules.admin.vo;

import java.time.Instant;
import java.util.List;

/**
 * Webhook 订阅响应视图（对齐前端 Admin 契约）。
 */
public record WebhookVo(
        long id,
        String name,
        String targetUrl,
        List<String> eventTypes,
        String status,
        Instant createdAt) {
}
