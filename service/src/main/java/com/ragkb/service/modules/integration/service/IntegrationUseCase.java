package com.ragkb.service.modules.integration.service;

import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;

/**
 * 外部集成用例占位。
 *
 * <p>TODO：实现 Webhook 签名投递、失败重试与死信重放。
 */
public interface IntegrationUseCase {

    Task deliverWebhook(TenantId tenantId, long subscriptionId, String eventId);

    Task replayWebhookDelivery(TenantId tenantId, long deliveryId, String idempotencyKey);
}
