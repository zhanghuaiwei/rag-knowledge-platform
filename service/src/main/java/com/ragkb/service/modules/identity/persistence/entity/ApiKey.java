package com.ragkb.service.modules.identity.persistence.entity;

import com.ragkb.service.common.persistence.BaseAuditEntity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ragkb.service.common.persistence.PostgresJsonbTypeHandler;
import java.time.Instant;

import java.util.List;

/**
 * {@code api_key} 表实体 —— 对齐 {@code deploy/ddl/init.sql}。
 *
 * <p>{@code scopes} 为 JSONB 数组列，映射 {@code List<String>}（{@link PostgresJsonbTypeHandler}），
 * 读取需 {@code @TableName(autoResultMap = true)} 配合 resultMap 使用。
 * 只存摘要（key_digest）与明文前缀，明文与摘要不进日志。
 */
@TableName(value = "api_key", autoResultMap = true)
public class ApiKey extends BaseAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String keyDigest;

    private String keyPrefix;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> scopes;

    private Integer rateLimitPerMinute;

    private String status;

    private Instant expiresAt;

    private Instant lastUsedAt;

    private Instant revokedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyDigest() {
        return keyDigest;
    }

    public void setKeyDigest(String keyDigest) {
        this.keyDigest = keyDigest;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public Integer getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(Integer rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

}
