package com.ragkb.service.modules.identity.vo;

/**
 * 当前激活租户响应视图（服务端从已验证身份推导，不信任客户端自报）。
 */
public record TenantContextVo(long tenantId, String tenantCode, String tenantRole) {
}
