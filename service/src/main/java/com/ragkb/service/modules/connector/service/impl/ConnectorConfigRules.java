package com.ragkb.service.modules.connector.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * 连接器配置规则表：各 {@code provider_key} 的 config 必填字段白名单。
 *
 * <p>创建 / 更新连接器时按本表校验 {@code connection_config}，拦截缺字段的脏配置
 * 直接落库（fail-closed）。真实适配器只覆盖 {@code local_directory} 与
 * {@code http_index}，其余类型（sharepoint/confluence/s3）允许保存配置，
 * 但连通性校验与同步由 Service 层 fail-closed 报「暂支持」错误。
 */
final class ConnectorConfigRules {

    /** 各连接器类型的必填配置字段（键名对齐前端 config 传参约定，camelCase）。 */
    private static final Map<String, List<String>> REQUIRED_FIELDS = Map.of(
            // 本地目录源：扫描根目录（开发/内网文件共享最小闭环）
            "local_directory", List.of("rootPath"),
            // HTTP 索引源：拉取一个 JSON 清单 URL
            "http_index", List.of("indexUrl"),
            // SharePoint 站点（SDK 适配器未实现，仅校验配置）
            "sharepoint", List.of("siteUrl"),
            // Confluence 空间（SDK 适配器未实现，仅校验配置）
            "confluence", List.of("baseUrl", "space"),
            // S3 桶（SDK 适配器未实现，仅校验配置）
            "s3", List.of("bucket", "region"));

    /** 凭证类配置键：出现在异常消息或日志中时必须脱敏（不回显取值）。 */
    static final List<String> SECRET_FIELD_KEYS = List.of(
            "password", "token", "accessToken", "apiKey", "clientSecret", "secretKey");

    private ConnectorConfigRules() {
    }

    /** 支持的 provider_key 清单（创建白名单用）。 */
    static List<String> supportedProviders() {
        return List.copyOf(REQUIRED_FIELDS.keySet());
    }

    /**
     * 校验 provider_key 是否受支持 + config 必填字段是否齐备；
     * 违规抛 {@link ErrorCode#BAD_REQUEST}（只报字段名，不回显配置值）。
     */
    static void validate(String providerKey, Map<String, Object> config) {
        List<String> required = REQUIRED_FIELDS.get(providerKey);
        if (required == null) {
            // 未知连接器类型直接拒绝创建（防止脏 provider_key 落库后无法路由）
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "不支持的连接器类型: " + providerKey + "，可选: " + supportedProviders());
        }
        if (config == null) {
            // config 列 NOT NULL（默认 '{}'），null 入参等价缺全部必填字段
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "缺少连接器配置，必填字段: " + required);
        }
        for (String field : required) {
            Object value = config.get(field);
            if (value == null || (value instanceof String text && text.isBlank())) {
                // 缺必填字段：只报字段名，不回显任何配置取值（防凭证泄漏）
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "连接器「" + providerKey + "」缺少必填配置字段: " + field);
            }
        }
    }

    /** 连接器 config 是否包含真实适配器（决定同步/校验能否真实执行）。 */
    static boolean hasAdapter(String providerKey) {
        return "local_directory".equals(providerKey) || "http_index".equals(providerKey);
    }
}
