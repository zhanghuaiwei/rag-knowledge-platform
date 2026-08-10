package com.ragkb.service.ports;

import com.ragkb.service.common.TenantId;

import java.util.Map;

/**
 * 事件发送端口：事务 outbox 消费者将领域事件投递到 transport（ADR-2）。
 * 只承担投递；可靠性与幂等由 outbox 表 + 幂等消费者保证，禁止自行双写。
 */
public interface EventTransportPort {

    void publish(TenantId tenantId, String topic, String eventType,
                 String aggregateId, long aggregateVersion, Map<String, Object> payload);
}
