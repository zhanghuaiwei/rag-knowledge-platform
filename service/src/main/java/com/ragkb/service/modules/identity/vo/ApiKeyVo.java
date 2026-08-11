package com.ragkb.service.modules.identity.vo;

import java.time.Instant;
import java.util.List;

/**
 * API Key 元数据响应视图（无明文；仅返回前缀）。
 */
public record ApiKeyVo(
        long id,
        String name,
        String keyPrefix,
        List<String> scopes,
        List<Long> kbIds,
        String status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt) {
}
