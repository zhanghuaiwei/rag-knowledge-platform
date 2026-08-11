package com.ragkb.service.modules.governance.vo;

import java.time.Instant;

/**
 * 保留策略响应视图。
 */
public record RetentionPolicyVo(
        long id,
        String name,
        String appliesTo,
        Long targetId,
        int durationMonths,
        String action,
        boolean enabled,
        Instant createdAt) {
}
