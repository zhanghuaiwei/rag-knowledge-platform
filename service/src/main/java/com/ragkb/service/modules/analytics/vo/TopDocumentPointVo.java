package com.ragkb.service.modules.analytics.vo;

/**
 * 热门文档点响应视图。
 */
public record TopDocumentPointVo(
        long documentId,
        String fileName,
        String kbName,
        long qaCount,
        long searchCount) {
}
