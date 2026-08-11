package com.ragkb.service.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文读取工具。
 *
 * <p>{@link #currentUserId()} 供 {@code AuditMetaObjectHandler} 自动填充
 * {@code create_by/update_by}：已认证用户态返回全局用户 id；API Key / 系统任务 /
 * 未认证返回 {@code null}（审计字段留空，由系统写入）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal actor && actor.authenticatedUserId() > 0) {
            return actor.authenticatedUserId();
        }
        return null;
    }
}
