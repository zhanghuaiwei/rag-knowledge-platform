package com.ragkb.service.ports;

import com.ragkb.service.common.TenantId;

import java.util.List;

/**
 * 内容源连接器端口：发现、增量拉取、源 ACL 与删除墓碑（00-README §1.2）。
 * Web URL 需 SSRF 防护；OAuth 回调不暴露第三方 token（06-架构方案 §2.3）。
 */
public interface ContentConnectorPort {

    /** 外部内容对象的稳定身份与同步状态（字段对齐 source_object 表）。 */
    record SourceObject(String externalId, String sourceUri, String sourceVersion,
                        String contentSha256, boolean tombstoned) {
    }

    List<SourceObject> discover(TenantId tenantId, long connectionId, String cursor);

    void health(TenantId tenantId, long connectionId);
}
