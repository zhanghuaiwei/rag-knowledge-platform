package com.ragkb.service.modules.document.dto;

import java.util.List;

/**
 * 文档元数据可编辑字段入参（租户 schema 驱动，最小子集）。
 */
public record UpdateDocumentMetadataDto(
        String title,
        String sensitivity,
        List<String> tags,
        String ownerName) {
}
