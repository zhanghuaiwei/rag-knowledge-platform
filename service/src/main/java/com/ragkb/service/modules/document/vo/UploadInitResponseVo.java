package com.ragkb.service.modules.document.vo;

import java.util.List;

/**
 * 上传初始化响应视图（真实实现为分片上传 + 安全扫描 GKB-03）。
 */
public record UploadInitResponseVo(
        String uploadId,
        long partSize,
        int partCount,
        List<Integer> uploadedParts,
        List<String> presignedPutUrls) {
}
