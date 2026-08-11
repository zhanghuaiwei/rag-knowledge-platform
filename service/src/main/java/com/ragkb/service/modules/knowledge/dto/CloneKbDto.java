package com.ragkb.service.modules.knowledge.dto;

import jakarta.validation.constraints.Size;

/**
 * 克隆知识库入参；name 为空时服务端自动生成「原名称（副本）」。
 */
public record CloneKbDto(@Size(max = 128) String name) {
}
