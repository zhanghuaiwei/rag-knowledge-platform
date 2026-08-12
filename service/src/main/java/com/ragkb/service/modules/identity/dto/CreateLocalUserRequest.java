package com.ragkb.service.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员创建本地用户入参（对齐 OpenAPI {@code CreateLocalUserRequest}）。
 *
 * <p>租户角色枚举与 {@code tenant_member_role.role} CHECK 一致；后端以唯一契约校验，
 * 防止越权分配未知角色。
 */
public record CreateLocalUserRequest(
        @NotBlank @Size(max = 254) String username,
        @NotBlank @Size(max = 254) @Email String email,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(min = 6, max = 72) String password,
        @NotEmpty List<@Pattern(regexp = "^(TENANT_ADMIN|SECURITY_ADMIN|KNOWLEDGE_ADMIN|AUDITOR|MEMBER)$") String> roles) {
}
