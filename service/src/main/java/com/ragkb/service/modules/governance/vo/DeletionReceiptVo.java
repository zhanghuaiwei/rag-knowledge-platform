package com.ragkb.service.modules.governance.vo;

import java.time.Instant;

/**
 * 删除证明响应视图。
 */
public record DeletionReceiptVo(
        String id,
        long taskId,
        long documentId,
        String fileName,
        String checksum,
        Instant deletedAt,
        String operator) {
}
