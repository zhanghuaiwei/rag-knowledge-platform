package com.ragkb.service.modules.identity.vo;

import java.time.Instant;
import java.util.List;

/**
 * 租户成员账号响应视图（管理中心用户列表；对齐 OpenAPI {@code User}）。
 *
 * <p>V0.5 变更：{@code role} 单角色 → {@code roles} 多角色列表；新增
 * {@code mustChangePassword}（首登/被重置后须改密，前端据此展示标记与改密引导）。
 */
public record UserVo(
        long id,
        String name,
        String email,
        String status,
        List<String> roles,
        String orgName,
        boolean mustChangePassword,
        Instant lastLoginAt) {
}
