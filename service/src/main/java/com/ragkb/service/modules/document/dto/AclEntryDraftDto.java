package com.ragkb.service.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 文档 ACL 草稿条目入参（F2.14 / GKB-04）。
 */
public record AclEntryDraftDto(
        @NotBlank String principalType,
        @NotBlank String principalName,
        @NotEmpty List<String> permissions) {
}
