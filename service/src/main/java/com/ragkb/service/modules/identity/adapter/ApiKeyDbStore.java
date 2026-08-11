package com.ragkb.service.modules.identity.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragkb.service.modules.identity.persistence.entity.ApiKey;
import com.ragkb.service.modules.identity.persistence.entity.ApiKeyKb;
import com.ragkb.service.modules.identity.persistence.mapper.ApiKeyKbMapper;
import com.ragkb.service.modules.identity.persistence.mapper.ApiKeyMapper;
import com.ragkb.service.modules.identity.port.ApiKeyStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code api_key} / {@code api_key_kb} 持久化实现（db.enabled=true 时激活）。
 *
 * <p>只存 digest + prefix（认证授权 §4.3）；scopes 为 JSONB 数组；allowedKbIds 经
 * {@code api_key_kb} 关联。吊销为逻辑删除（status=REVOKED），不物理删。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class ApiKeyDbStore implements ApiKeyStorePort {

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyKbMapper apiKeyKbMapper;

    public ApiKeyDbStore(ApiKeyMapper apiKeyMapper, ApiKeyKbMapper apiKeyKbMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyKbMapper = apiKeyKbMapper;
    }

    @Override
    public List<ApiKeyRecord> list(long tenantId) {
        List<ApiKey> keys = apiKeyMapper.selectList(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getTenantId, tenantId).orderByDesc(ApiKey::getCreateTime));
        return keys.stream().map(key -> toRecord(key, kbIdsOf(tenantId, key.getId()))).toList();
    }

    @Override
    public Optional<ApiKeyRecord> findById(long tenantId, long keyId) {
        ApiKey key = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getTenantId, tenantId)
                .eq(ApiKey::getId, keyId));
        return Optional.ofNullable(key).map(k -> toRecord(k, kbIdsOf(tenantId, k.getId())));
    }

    @Override
    public Optional<ApiKeyRecord> findActiveByPrefixAndDigest(String keyPrefix, String digest) {
        ApiKey key = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKeyPrefix, keyPrefix)
                .eq(ApiKey::getKeyDigest, digest)
                .eq(ApiKey::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (key == null) {
            return Optional.empty();
        }
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(Instant.now())) {
            return Optional.empty(); // 已过期按不存在处理
        }
        return Optional.of(toRecord(key, kbIdsOf(key.getTenantId(), key.getId())));
    }

    @Override
    @Transactional
    public long create(CreateCommand command) {
        ApiKey key = new ApiKey();
        key.setTenantId(command.tenantId());
        key.setName(command.name());
        key.setScopes(command.scopes());
        key.setKeyDigest(command.keyDigest());
        key.setKeyPrefix(command.keyPrefix());
        key.setRateLimitPerMinute(command.rateLimitPerMinute());
        key.setStatus("ACTIVE");
        key.setExpiresAt(command.expiresAt());
        key.setCreateBy(command.createdBy());
        apiKeyMapper.insert(key);
        insertKbLinks(command.tenantId(), key.getId(), command.allowedKbIds());
        return key.getId();
    }

    @Override
    public void revoke(long tenantId, long keyId) {
        apiKeyMapper.update(null, new LambdaUpdateWrapper<ApiKey>()
                .eq(ApiKey::getTenantId, tenantId)
                .eq(ApiKey::getId, keyId)
                .set(ApiKey::getStatus, "REVOKED")
                .set(ApiKey::getRevokedAt, Instant.now()));
    }

    @Override
    @Transactional
    public void updateDigestAndPrefix(long tenantId, long keyId, String newDigest, String newPrefix) {
        apiKeyMapper.update(null, new LambdaUpdateWrapper<ApiKey>()
                .eq(ApiKey::getTenantId, tenantId)
                .eq(ApiKey::getId, keyId)
                .set(ApiKey::getKeyDigest, newDigest)
                .set(ApiKey::getKeyPrefix, newPrefix));
    }

    @Override
    public void touchLastUsedAt(long tenantId, long keyId, Instant usedAt) {
        apiKeyMapper.update(null, new LambdaUpdateWrapper<ApiKey>()
                .eq(ApiKey::getTenantId, tenantId)
                .eq(ApiKey::getId, keyId)
                .set(ApiKey::getLastUsedAt, usedAt));
    }

    // ---------- 内部工具 ----------

    /** 一次性取出租户下多个 key 的 kb 关联，避免循环查库（N+1 零容忍）。 */
    private List<Long> kbIdsOf(long tenantId, long keyId) {
        return apiKeyKbMapper.selectList(new LambdaQueryWrapper<ApiKeyKb>()
                        .eq(ApiKeyKb::getTenantId, tenantId)
                        .eq(ApiKeyKb::getApiKeyId, keyId))
                .stream()
                .map(ApiKeyKb::getKbId)
                .collect(Collectors.toList());
    }

    private void insertKbLinks(long tenantId, long keyId, List<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return;
        }
        for (Long kbId : kbIds) {
            ApiKeyKb link = new ApiKeyKb();
            link.setTenantId(tenantId);
            link.setApiKeyId(keyId);
            link.setKbId(kbId);
            apiKeyKbMapper.insert(link);
        }
    }

    private ApiKeyRecord toRecord(ApiKey key, List<Long> kbIds) {
        return new ApiKeyRecord(key.getId(), key.getTenantId(), key.getName(), key.getKeyPrefix(),
                key.getScopes() != null ? key.getScopes() : List.of(), kbIds,
                key.getStatus(), key.getExpiresAt(), key.getLastUsedAt(), key.getRevokedAt(),
                key.getCreateTime());
    }
}
