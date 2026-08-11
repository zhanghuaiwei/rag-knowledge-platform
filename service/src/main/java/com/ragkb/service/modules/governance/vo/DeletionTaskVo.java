package com.ragkb.service.modules.governance.vo;

import java.time.Instant;

/**
 * 删除任务响应视图（软删除为异步任务）。
 */
public record DeletionTaskVo(
        long id,
        long documentId,
        String fileName,
        String reason,
        String requestedBy,
        String status,
        Instant createdAt,
        DeletionProgressVo progress) {
}
