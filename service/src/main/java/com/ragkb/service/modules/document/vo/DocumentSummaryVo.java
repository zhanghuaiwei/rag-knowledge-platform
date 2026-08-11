package com.ragkb.service.modules.document.vo;

/**
 * 文档列表摘要响应视图（对齐前端 Document 列表契约）。
 */
public record DocumentSummaryVo(
        long id,
        long kbId,
        String kbName,
        String title,
        String fileName,
        String fileExt,
        String mimeType,
        String sourceType,
        long fileSize,
        int versionNo,
        String ingestStatus,
        String reviewStatus,
        String sensitivity,
        String ownerName,
        long chunkCount,
        String updatedAt) {
}
