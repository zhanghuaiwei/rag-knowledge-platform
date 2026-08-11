package com.ragkb.service.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建/更新组织入参。
 */
public record OrgDto(@NotBlank @Size(max = 128) String name, Long parentId) {
}
