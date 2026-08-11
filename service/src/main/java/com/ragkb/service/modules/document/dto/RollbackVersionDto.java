package com.ragkb.service.modules.document.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 回滚到指定文档版本入参。
 */
public record RollbackVersionDto(@NotNull Integer versionNo) {
}
