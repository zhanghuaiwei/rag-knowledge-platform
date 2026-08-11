package com.ragkb.service.modules.admin.vo;

import java.time.Instant;

/**
 * 管理中心用户响应视图（对齐前端 Admin 契约）。
 */
public record UserVo(
        long id,
        String name,
        String email,
        String status,
        String role,
        String orgName,
        Instant lastLoginAt) {
}
