package com.ragkb.service.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 认证域 DTO：会话 / 租户 / API Key（对齐前端契约，OpenAPI 草案中 session/tenant 形状）。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 当前激活租户（服务端从已验证身份推导，不信任客户端自报）。 */
    public record TenantContext(long tenantId, String tenantCode, String tenantRole) {
    }

    /** 当前登录用户会话概览。 */
    public record AuthSession(
            long userId,
            String subjectKey,
            String displayName,
            TenantContext activeTenant,
            List<TenantContext> tenants,
            List<String> scopes) {
    }

    /** API Key 元数据（无明文；仅返回前缀）。 */
    public record ApiKey(
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

    /** 创建 API Key 入参。 */
    public record ApiKeyCreateRequest(
            @NotBlank @Size(max = 128) String name,
            @NotEmpty List<String> scopes,
            List<Long> allowedKbIds,
            Instant expiresAt) {
    }

    /** 创建/轮换结果：明文只出现一次。 */
    public record ApiKeyCreated(ApiKey key, String secret) {
    }
}
