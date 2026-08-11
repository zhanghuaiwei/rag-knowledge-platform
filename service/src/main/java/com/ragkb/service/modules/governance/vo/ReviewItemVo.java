package com.ragkb.service.modules.governance.vo;

/**
 * 内容审核待办响应视图（F2.13）。
 */
public record ReviewItemVo(
        long documentId,
        String title,
        String kbName,
        String submitter,
        String sensitivity,
        String submittedAt,
        long commentCount) {
}
