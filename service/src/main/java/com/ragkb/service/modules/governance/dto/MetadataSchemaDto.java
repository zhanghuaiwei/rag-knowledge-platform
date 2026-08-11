package com.ragkb.service.modules.governance.dto;

import com.ragkb.service.modules.governance.vo.MetadataFieldVo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建/更新元数据 schema 入参（GKB-04）。
 */
public record MetadataSchemaDto(
        @NotBlank String name,
        String description,
        @NotEmpty List<@NotNull MetadataFieldVo> fields) {
}
