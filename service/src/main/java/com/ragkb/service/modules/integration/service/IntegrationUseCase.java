package com.ragkb.service.modules.integration.service;

import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;

/**
 * 外部集成用例：Webhook 手动投递与死信重放入口。
 *
 * <p>实现见 {@code WebhookDeliveryServiceImpl}（签名投递 / 失败重试 / 死信处理）；
 * 常规投递由其内置的轮询器自动驱动，本接口面向人工干预场景。
 */
public interface IntegrationUseCase {

    /**
     * 为指定订阅手动投递一次指定 outbox 事件（不等轮询周期）。
     *
     * @param eventId outbox_event 主键 id 的字符串化（与 WebhookDeliveryVo.eventId 一致）
     */
    Task deliverWebhook(TenantId tenantId, long subscriptionId, String eventId);

    /**
     * 重放一条死信（或任意终态）投递记录：重置为待投递后由下一轮投递周期执行。
     */
    Task replayWebhookDelivery(TenantId tenantId, long deliveryId, String idempotencyKey);
}
