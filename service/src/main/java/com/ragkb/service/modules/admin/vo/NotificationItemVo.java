package com.ragkb.service.modules.admin.vo;

import java.time.Instant;

/**
 * 通知条目响应视图（对齐前端 Admin 契约）。
 */
public record NotificationItemVo(
        long id,
        String kind,
        String level,
        String title,
        String body,
        boolean read,
        Instant createdAt,
        String href) {
}
