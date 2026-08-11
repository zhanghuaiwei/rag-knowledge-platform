package com.ragkb.service.modules.governance.vo;

import java.time.Instant;
import java.util.List;

/**
 * 元数据 schema 响应视图（GKB-04）。
 */
public record MetadataSchemaVo(
        long id,
        String name,
        String description,
        List<MetadataFieldVo> fields,
        String status,
        Instant updatedAt) {
}
