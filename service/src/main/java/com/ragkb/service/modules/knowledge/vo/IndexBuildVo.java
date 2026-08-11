package com.ragkb.service.modules.knowledge.vo;

import java.time.Instant;

/**
 * 索引构建任务响应视图。
 */
public record IndexBuildVo(
        long id,
        int profileVersion,
        String status,
        long inputDocuments,
        long chunkCount,
        long failedCount,
        Object qualityGate,
        String errorCode,
        Instant createdAt,
        Instant publishedAt) {
}
