package com.ragkb.service.modules.connector.dto;

import java.util.Map;

/**
 * 更新内容源连接器入参。
 */
public record ConnectorUpdateDto(String name, Map<String, Object> config, Boolean enabled) {
}
