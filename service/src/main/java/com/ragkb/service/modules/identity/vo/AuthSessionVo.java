package com.ragkb.service.modules.identity.vo;

import java.util.List;

/**
 * 当前登录用户会话响应视图。
 */
public record AuthSessionVo(
        long userId,
        String subjectKey,
        String displayName,
        TenantContextVo activeTenant,
        List<TenantContextVo> tenants,
        List<String> scopes) {
}
