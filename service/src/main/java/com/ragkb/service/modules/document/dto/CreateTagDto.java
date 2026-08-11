package com.ragkb.service.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建标签入参。
 */
public record CreateTagDto(@NotBlank @Size(max = 64) String name) {
}
