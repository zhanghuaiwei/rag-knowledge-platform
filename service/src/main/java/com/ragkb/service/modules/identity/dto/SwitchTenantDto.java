package com.ragkb.service.modules.identity.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 切换当前租户入参。
 */
public record SwitchTenantDto(@NotNull Long tenantId) {
}
