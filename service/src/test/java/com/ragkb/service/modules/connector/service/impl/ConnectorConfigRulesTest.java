package com.ragkb.service.modules.connector.service.impl;

import com.ragkb.service.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连接器配置规则纯逻辑测试（不依赖数据库/网络）：
 * 覆盖 provider_key 白名单、必填字段校验与适配器覆盖判定。
 */
class ConnectorConfigRulesTest {

    @Test
    void supportedProvidersCoverExpectedTypes() {
        // 支持矩阵必须覆盖两类真实适配器 + 三类仅配置校验的企业源
        var providers = ConnectorConfigRules.supportedProviders();
        assertEquals(5, providers.size());
        assertTrue(providers.containsAll(java.util.List.of(
                "local_directory", "http_index", "sharepoint", "confluence", "s3")));
    }

    @Test
    void validateAcceptsCompleteConfigs() {
        // 各类型配置齐备时校验通过（含额外非必填字段不报错）
        assertDoesNotThrow(() -> ConnectorConfigRules.validate("local_directory",
                Map.of("rootPath", "/data/share", "kbId", 2)));
        assertDoesNotThrow(() -> ConnectorConfigRules.validate("http_index",
                Map.of("indexUrl", "https://example.com/index.json")));
        assertDoesNotThrow(() -> ConnectorConfigRules.validate("confluence",
                Map.of("baseUrl", "https://confluence.example.com", "space", "DEV")));
        assertDoesNotThrow(() -> ConnectorConfigRules.validate("s3",
                Map.of("bucket", "kb-source", "region", "cn-north-1")));
    }

    @Test
    void validateRejectsUnknownProvider() {
        // 未知 provider_key 拒绝创建（防止脏类型落库后无法路由适配器）
        ApiException error = assertThrows(ApiException.class,
                () -> ConnectorConfigRules.validate("ftp", Map.of("host", "x")));
        assertTrue(error.getMessage().contains("不支持的连接器类型"));
    }

    @Test
    void validateRejectsMissingRequiredFields() {
        // 缺单个必填字段：报字段名且不回显其他配置值（防凭证泄漏）
        ApiException missing = assertThrows(ApiException.class,
                () -> ConnectorConfigRules.validate("sharepoint", Map.of("other", 1)));
        assertTrue(missing.getMessage().contains("siteUrl"));

        // 多字段类型逐项校验：缺 space 也应拦截
        ApiException missingSpace = assertThrows(ApiException.class,
                () -> ConnectorConfigRules.validate("confluence",
                        Map.of("baseUrl", "https://confluence.example.com")));
        assertTrue(missingSpace.getMessage().contains("space"));

        // 空白字符串等价缺失
        ApiException blank = assertThrows(ApiException.class,
                () -> ConnectorConfigRules.validate("local_directory", Map.of("rootPath", "  ")));
        assertTrue(blank.getMessage().contains("rootPath"));

        // null 配置等价缺全部必填字段
        ApiException nullConfig = assertThrows(ApiException.class,
                () -> ConnectorConfigRules.validate("http_index", null));
        assertTrue(nullConfig.getMessage().contains("indexUrl"));
    }

    @Test
    void adapterCoverageOnlyForImplementedTypes() {
        // 仅 local_directory / http_index 有真实适配器；企业源 fail-closed（同步/校验明确报不支持）
        assertTrue(ConnectorConfigRules.hasAdapter("local_directory"));
        assertTrue(ConnectorConfigRules.hasAdapter("http_index"));
        assertFalse(ConnectorConfigRules.hasAdapter("sharepoint"));
        assertFalse(ConnectorConfigRules.hasAdapter("confluence"));
        assertFalse(ConnectorConfigRules.hasAdapter("s3"));
    }
}
