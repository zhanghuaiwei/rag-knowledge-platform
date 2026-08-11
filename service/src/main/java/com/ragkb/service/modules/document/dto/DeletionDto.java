package com.ragkb.service.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档删除入参（软删除为异步任务，reason 留痕）。
 */
public record DeletionDto(@NotBlank @Size(max = 2048) String reason) {
}
