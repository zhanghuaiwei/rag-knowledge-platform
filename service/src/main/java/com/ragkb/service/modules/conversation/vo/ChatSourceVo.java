package com.ragkb.service.modules.conversation.vo;

/**
 * 回答引用来源响应视图。
 */
public record ChatSourceVo(
        String chunkId,
        long documentId,
        String fileName,
        int pageNo,
        String sectionTitle,
        double score) {
}
