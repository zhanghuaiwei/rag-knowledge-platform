package com.ragkb.service.modules.connector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 连接器配置校验入参。
 */
public record ConnectorValidateDto(@NotBlank String providerKey, @NotNull Map<String, Object> config) {
}
