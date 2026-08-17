package com.ragkb.service.modules.integration.port;

import java.util.Map;

/**
 * 事务内 outbox 追加端口（ADR-2 事务发件箱）。
 *
 * <p>业务模块在自身事务内调用本端口追加领域事件行；由独立 outbox 消费者
 * 读取 {@code status IN ('NEW','FAILED')} 的行并投递到 {@link com.ragkb.service.common.event.EventTransportPort}，
 * 保证「数据库提交即事件可见」，禁止业务模块直接双写消息中间件。
 *
 * <p>实现见 {@code integration.service.impl.OutboxAppender}（同一事务，仅 INSERT）。
 */
public interface OutboxPort {

    /**
     * 追加一条 outbox 事件（与业务写操作同事务提交）。
     *
     * @param tenantId        租户 id
     * @param aggregateType   聚合类型（如 DOCUMENT / KB）
     * @param aggregateId     聚合 id（字符串化）
     * @param aggregateVersion 聚合版本（>=1，事件序）
     * @param eventType       事件类型（如 document.uploaded）
     * @param topic           主题（ingestion / index / sync / webhook / governance / general）
     * @param payload         事件载荷（须为 JSON 对象）
     */
    void append(long tenantId, String aggregateType, String aggregateId, long aggregateVersion,
                String eventType, String topic, Map<String, Object> payload);
}
