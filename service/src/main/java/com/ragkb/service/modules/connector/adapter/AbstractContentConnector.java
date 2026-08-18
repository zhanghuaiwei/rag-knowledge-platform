package com.ragkb.service.modules.connector.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.modules.connector.persistence.entity.SourceConnection;
import com.ragkb.service.modules.connector.persistence.mapper.SourceConnectionMapper;
import com.ragkb.service.modules.connector.port.ContentConnectorPort;

import java.util.Map;

/**
 * 内容源适配器公共基类：按 connectionId 加载 {@code source_connection.config}
 * 并解析为 Map，供具体适配器（本地目录 / HTTP 索引 / 未来 SDK 接入）复用。
 *
 * <p>config 列为 JSONB（实体映射 String）：空值按空 Map 处理，由各适配器的
 * 必填字段校验兜底报错（fail-closed）。
 */
abstract class AbstractContentConnector implements ContentConnectorPort {

    /** JSONB 字符串 → Map 的解析器（Spring 容器的全局 ObjectMapper）。 */
    private final ObjectMapper objectMapper;

    /** 连接配置表读取（同模块持久化，模块内合法依赖）。 */
    private final SourceConnectionMapper sourceConnectionMapper;

    /** 本适配器负责的连接器类型（构造时固化，见各实现类）。 */
    private final String providerKey;

    protected AbstractContentConnector(ObjectMapper objectMapper,
                                       SourceConnectionMapper sourceConnectionMapper,
                                       String providerKey) {
        this.objectMapper = objectMapper;
        this.sourceConnectionMapper = sourceConnectionMapper;
        this.providerKey = providerKey;
    }

    @Override
    public String providerKey() {
        return providerKey;
    }

    /** 按 id 加载连接配置行；不存在按业务异常处理（同步执行器捕获后置 FAILED）。 */
    protected SourceConnection requireConnection(long connectionId) {
        SourceConnection connection = sourceConnectionMapper.selectById(connectionId);
        if (connection == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "连接器不存在: " + connectionId);
        }
        return connection;
    }

    /** config（JSONB → String）解析为 Map；空/脏值按空 Map 处理不中断（必填校验兜底）。 */
    protected Map<String, Object> parseConfig(SourceConnection connection) {
        String config = connection.getConfig();
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            // 历史脏数据：按空配置处理，让必填字段校验给出明确错误（不吞错假报成功）
            return Map.of();
        }
    }
}
