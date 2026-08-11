package com.ragkb.service.modules.connector.vo;

import java.time.Instant;

/**
 * 内容源连接器响应视图（对齐前端 Connector 契约；计数/同步模式为产品契约所需）。
 */
public record ConnectorVo(
        long id,
        String name,
        String providerKey,
        String syncMode,
        String status,
        Instant lastSuccessAt,
        String lastErrorCode,
        long cursorAgeMin,
        ConnectorCountsVo counts) {
}
