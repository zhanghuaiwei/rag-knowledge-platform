package com.ragkb.service.modules.governance.vo;

import java.time.Instant;
import java.util.List;

/**
 * 法律保全响应视图。
 */
public record LegalHoldVo(
        long id,
        String name,
        List<Long> documentIds,
        String reason,
        String createdBy,
        Instant createdAt,
        Instant releasedAt) {
}
