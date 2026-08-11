package com.ragkb.service.modules.connector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 创建内容源连接器入参。
 */
public record ConnectorCreateDto(
        @NotBlank @Size(max = 128) String name,
        @NotBlank String providerKey,
        Map<String, Object> config,
        Boolean enabled) {
}
