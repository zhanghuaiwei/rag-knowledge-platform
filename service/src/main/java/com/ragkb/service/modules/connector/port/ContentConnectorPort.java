package com.ragkb.service.modules.connector.port;

import com.ragkb.service.common.model.TenantId;

import java.util.List;
import java.util.Map;

/**
 * 内容源连接器端口：发现、增量拉取、源 ACL 与删除墓碑（00-README §1.2）。
 * Web URL 需 SSRF 防护；OAuth 回调不暴露第三方 token（06-架构方案 §2.3）。
 *
 * <p>实现（adapter 包）自带 {@link #providerKey()} 身份：Service 按
 * {@code source_connection.provider_key} 路由到对应适配器；未覆盖的类型
 * fail-closed（创建可仅做配置校验，同步/连通性校验明确报「暂不支持」，
 * 不假报成功）。
 */
public interface ContentConnectorPort {

    /** 外部内容对象的稳定身份与同步状态（字段对齐 source_object 表）。 */
    record SourceObject(String externalId, String sourceUri, String sourceVersion,
                        String contentSha256, boolean tombstoned) {
    }

    /**
     * 本适配器负责的连接器类型（对应 {@code source_connection.provider_key}，
     * 约定小写加下划线，如 {@code local_directory} / {@code http_index}）。
     */
    String providerKey();

    /**
     * 轻量连通性 / 配置校验（validate 端点与同步前置检查复用）：
     * 配置缺失或目标不可达时抛业务异常；实现方不得吞错假报成功（fail-closed）。
     *
     * @param config 连接器配置（来自请求体或 {@code source_connection.config} JSON）
     */
    void validate(Map<String, Object> config);

    /**
     * 发现外部内容对象清单（同步执行器逐对象 upsert 到 source_object）。
     *
     * @param cursor 上次同步游标（首次为 null；快照式源可忽略，由调用方做全量对账）
     */
    List<SourceObject> discover(TenantId tenantId, long connectionId, String cursor);

    /** 存活探测：目标不可达时抛业务异常（语义与 {@link #validate} 一致）。 */
    void health(TenantId tenantId, long connectionId);
}
