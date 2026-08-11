package com.ragkb.service.modules.document.vo;

/**
 * 文档版本响应视图（对齐前端 Document 详情契约）。
 */
public record DocumentVersionVo(
        int versionNo,
        long fileSize,
        String ingestStatus,
        String safetyStatus,
        long chunkCount,
        String createdBy,
        String createdAt) {
}
