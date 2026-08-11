package com.ragkb.service.modules.document.vo;

import java.util.List;

/**
 * 文档详情响应视图（对齐前端 Document 详情契约）。
 */
public record DocumentDetailVo(
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
        String updatedAt,
        List<DocumentVersionVo> versions,
        List<String> tags,
        boolean isFavorite,
        Integer retryCount) {
}
