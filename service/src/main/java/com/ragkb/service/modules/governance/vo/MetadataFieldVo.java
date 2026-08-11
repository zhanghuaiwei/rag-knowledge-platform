package com.ragkb.service.modules.governance.vo;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 元数据字段视图（schema 响应与 schema 入参共用，故保留校验注解）。
 */
public record MetadataFieldVo(
        @NotBlank String key,
        @NotBlank String label,
        @NotBlank String type,
        boolean required,
        List<String> options) {
}
