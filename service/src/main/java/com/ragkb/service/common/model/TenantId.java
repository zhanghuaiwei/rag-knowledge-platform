package com.ragkb.service.common.model;

/**
 * 租户标识值对象。调用方不能自报 tenant，tenant 来自已验证身份/服务上下文
 * （00-README §4 统一约定；05-技术选型 §3.3 多租户隔离）。
 */
public record TenantId(long value) {

    public TenantId {
        if (value <= 0) {
            throw new IllegalArgumentException("tenantId 必须为正整数");
        }
    }
}
