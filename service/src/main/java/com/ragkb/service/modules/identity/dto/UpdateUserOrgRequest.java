package com.ragkb.service.modules.identity.dto;

/**
 * 调整成员所属组织入参；orgId 为 null 表示移出组织（对齐 OpenAPI PATCH /users/{userId}/org）。
 *
 * <p>identity 模块自有 DTO（原 admin 模块 {@code UserOrgDto} 跨模块不可引用）。
 */
public record UpdateUserOrgRequest(Long orgId) {
}
