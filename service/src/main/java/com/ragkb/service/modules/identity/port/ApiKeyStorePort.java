package com.ragkb.service.modules.identity.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * API Key 持久化存储（机器访问凭证，认证授权 §4.3）。
 *
 * <p>只有摘要（SHA-256 + pepper）入库，明文与摘要不进日志；allowedKbIds 经 {@code api_key_kb} 关联。
 * 由 {@code ApiKeyDbStore}（db.enabled=true）实现；无 DB 时 AuthService 返回明确错误。
 */
public interface ApiKeyStorePort {

    /** API Key 元数据（无明文）。 */
    record ApiKeyRecord(long id, long tenantId, String name, String keyPrefix, List<String> scopes,
                        List<Long> allowedKbIds, String status, Instant expiresAt,
                        Instant lastUsedAt, Instant revokedAt, Instant createdAt) {
    }

    /** 创建入参（已含 digest/prefix，由调用方经 {@code ApiKeyCrypto} 生成）。 */
    record CreateCommand(long tenantId, String name, List<String> scopes, List<Long> allowedKbIds,
                         Instant expiresAt, long createdBy, String keyDigest, String keyPrefix,
                         int rateLimitPerMinute) {
    }

    /** 租户下全部 API Key（含已吊销，列表展示）。 */
    List<ApiKeyRecord> list(long tenantId);

    /** 按主键查（租户隔离）。 */
    Optional<ApiKeyRecord> findById(long tenantId, long keyId);

    /** 按 prefix + digest 查 ACTIVE key（请求认证用）；无/吊销/过期返回空。 */
    Optional<ApiKeyRecord> findActiveByPrefixAndDigest(String keyPrefix, String digest);

    /** 创建并返回自增 id（scopes JSONB + allowedKbIds 写 api_key_kb）。 */
    long create(CreateCommand command);

    /** 吊销（status=REVOKED + revoked_at），逻辑删除不物理删。 */
    void revoke(long tenantId, long keyId);

    /** 轮换：更新 digest 与 prefix（status 保持 ACTIVE）。 */
    void updateDigestAndPrefix(long tenantId, long keyId, String newDigest, String newPrefix);

    /** 记录最近使用时间（应限频调用，避免每请求写热点行）。 */
    void touchLastUsedAt(long tenantId, long keyId, Instant usedAt);
}
