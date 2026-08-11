package com.ragkb.service.modules.admin.vo;

import java.time.Instant;

/**
 * 审计日志条目响应视图（对齐前端 Admin 契约）。
 */
public record AuditLogEntryVo(
        long id,
        String actor,
        String actorType,
        String action,
        String resourceType,
        String resourceId,
        String result,
        String reasonCode,
        String requestId,
        Instant occurredAt) {
}
