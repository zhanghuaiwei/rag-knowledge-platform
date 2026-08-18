package com.ragkb.service.modules.admin.vo;

import java.time.Instant;
import java.util.List;

/**
 * Webhook 订阅响应视图（对齐前端 Admin 契约）。
 *
 * <p>{@code secret} 为创建时一次性返回的签名密钥明文（对齐 ApiKeyCreatedVo 模式）：
 * 仅 createWebhook 响应非空，listWebhooks/toggleWebhook 等一律返回 null，
 * 服务端只在 secret_ref 列保存该值用于 HMAC 签名，绝不写日志。
 */
public record WebhookVo(
        long id,
        String name,
        String targetUrl,
        List<String> eventTypes,
        String status,
        Instant createdAt,
        String secret) {
}
