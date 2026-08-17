package com.ragkb.service.modules.integration.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.modules.integration.persistence.entity.OutboxEvent;
import com.ragkb.service.modules.integration.persistence.mapper.OutboxEventMapper;
import com.ragkb.service.modules.integration.port.OutboxPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * {@link OutboxPort} 的数据库实现：在调用方事务内追加一条 outbox 事件。
 *
 * <p>关键点：
 * <ul>
 *   <li>{@code REQUIRED} 传播：与业务写（document/document_version/parse_task）同一事务提交，
 *       保证「业务可见即事件可见」，不单独开事务；</li>
 *   <li>{@code payload} 序列化为 JSON 对象字符串，经 {@code CAST(? AS jsonb)} 写入
 *       （见 {@link OutboxEventMapper#insertWithJsonb}），满足 DDL 的
 *       {@code ck_outbox_payload CHECK (jsonb_typeof(payload) = 'object')}；</li>
 *   <li>{@code event_id / status / attempt_count / available_at / occurred_at} 交由数据库默认值，
 *       应用只写业务字段，避免与触发器/DEFAULT 语义打架。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class OutboxAppender implements OutboxPort {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public OutboxAppender(OutboxEventMapper outboxEventMapper, ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(long tenantId, String aggregateType, String aggregateId, long aggregateVersion,
                       String eventType, String topic, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(tenantId);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setAggregateVersion(aggregateVersion);
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setPayload(toJson(payload));
        event.setStatus("NEW");
        event.setAttemptCount(0);
        event.setAvailableAt(Instant.now());
        event.setOccurredAt(Instant.now());
        outboxEventMapper.insertWithJsonb(event);
    }

    /** payload 必须是 JSON 对象（对应 DDL 的 jsonb_typeof = 'object' CHECK）。 */
    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "outbox 载荷序列化失败", e);
        }
    }
}
