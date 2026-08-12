package com.ragkb.service.modules.identity.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 覆盖式替换租户角色入参（对齐 OpenAPI {@code RoleSetRequest}；overwrite 语义）。
 */
public record RoleSetRequest(
        @NotEmpty List<@Pattern(regexp = "^(TENANT_ADMIN|SECURITY_ADMIN|KNOWLEDGE_ADMIN|AUDITOR|MEMBER)$") String> roles) {
}
