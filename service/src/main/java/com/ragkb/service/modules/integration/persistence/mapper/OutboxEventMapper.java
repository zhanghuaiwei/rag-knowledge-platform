package com.ragkb.service.modules.integration.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.integration.persistence.entity.OutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code outbox_event} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/OutboxEventMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 *
 * <p>{@code insertWithJsonb}：实体 {@code payload} 为 {@code String}（骨架约定 JSONB 列映射 String），
 * 直接走 BaseMapper.insert 会把字符串当作 varchar 写入 JSONB 列而报类型错，故提供显式
 * {@code CAST(? AS jsonb)} 的 INSERT（{@code event_id/status/attempt_count/时间列} 由数据库默认值兜底）。
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /** 以 JSONB 强转方式插入 outbox 事件（payload 必须是合法 JSON 对象字符串）。 */
    @Insert("""
            INSERT INTO outbox_event (
                tenant_id, aggregate_type, aggregate_id, aggregate_version,
                event_type, topic, payload, status, attempt_count,
                available_at, occurred_at
            ) VALUES (
                #{tenantId}, #{aggregateType}, #{aggregateId}, #{aggregateVersion},
                #{eventType}, #{topic}, CAST(#{payload} AS jsonb), #{status}, #{attemptCount},
                #{availableAt}, #{occurredAt}
            )
            """)
    int insertWithJsonb(OutboxEvent event);
}
