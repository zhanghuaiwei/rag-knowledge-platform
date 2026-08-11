package com.ragkb.service.modules.connector.vo;

import java.time.Instant;
import java.util.List;

/**
 * 同步任务响应视图（对齐前端 Connector 同步契约）。
 */
public record SyncJobVo(
        long id,
        long connectionId,
        String syncType,
        String status,
        long discovered,
        List<String> failedObjects,
        Instant lastSuccessAt,
        String errorCode,
        Instant createdAt) {
}
