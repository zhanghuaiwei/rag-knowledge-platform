package com.ragkb.service.modules.governance.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 启用/停用保留策略入参。
 */
public record RetentionPolicyToggleDto(@NotNull Boolean enabled) {
}
