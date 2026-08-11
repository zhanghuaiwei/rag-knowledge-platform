package com.ragkb.service.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 上传初始化入参（真实实现为分片上传 + 安全扫描 GKB-03）。
 */
public record UploadInitDto(
        @NotNull Long kbId,
        @NotBlank @Size(max = 256) String fileName,
        @NotNull Long fileSize,
        String mimeType,
        String sha256,
        String title,
        String sensitivity) {
}
