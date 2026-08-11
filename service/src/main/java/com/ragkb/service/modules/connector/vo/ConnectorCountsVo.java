package com.ragkb.service.modules.connector.vo;

/**
 * 连接器同步计数响应视图（产品契约所需）。
 */
public record ConnectorCountsVo(
        long discovered,
        long created,
        long updated,
        long deleted,
        long failed) {
}
