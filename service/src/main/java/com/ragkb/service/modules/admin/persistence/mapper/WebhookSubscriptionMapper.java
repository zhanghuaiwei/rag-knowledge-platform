package com.ragkb.service.modules.admin.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.admin.persistence.entity.WebhookSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code webhook_subscription} 表 Mapper：单表 CRUD 走 {@link BaseMapper}，
 * JSONB 列的插入走 {@code resources/mapper/WebhookSubscriptionMapper.xml}
 * 的 {@link #insertWithJsonb}（CAST 强转，对齐 OutboxEventMapper 的处理方式）。
 */
@Mapper
public interface WebhookSubscriptionMapper extends BaseMapper<WebhookSubscription> {

    /**
     * 以 JSONB 强转方式插入订阅：实体 {@code eventTypes} 为 {@code String}
     * （骨架约定 JSONB 列映射 String），直接 BaseMapper.insert 会把字符串按 varchar
     * 写入 JSONB 列而报类型错，故提供显式 {@code CAST(#{eventTypes} AS jsonb)} 的 INSERT；
     * 其余状态/时间列由数据库默认值兜底，主键回填实体 id。
     */
    int insertWithJsonb(@Param("entity") WebhookSubscription entity);
}
