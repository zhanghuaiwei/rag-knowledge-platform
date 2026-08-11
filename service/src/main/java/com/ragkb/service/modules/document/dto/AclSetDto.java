package com.ragkb.service.modules.document.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 覆盖式写入 ACL 入参：调用方传入完整目标列表（白名单语义）。
 */
public record AclSetDto(@NotEmpty List<@NotNull AclEntryDraftDto> entries) {
}
