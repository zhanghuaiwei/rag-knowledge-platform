package com.ragkb.service.modules.admin.dto;

/**
 * 调整用户组织入参；orgId 为 null 表示移出组织。
 */
public record UserOrgDto(Long orgId) {
}
