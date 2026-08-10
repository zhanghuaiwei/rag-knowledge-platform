package com.ragkb.service.ports;

import com.ragkb.service.common.TenantId;

import java.util.List;
import java.util.Map;

/**
 * 统一搜索索引端口：BM25 + 向量 + 过滤 + 高亮 + alias 原子切换（ADR-3）。
 * 索引是可重建派生数据；字段/维度由不可变 index profile 决定，不在原地修改。
 * 具体 chunk 结构（ChunkDocument）在索引模块实现时定义并映射为 Map。
 */
public interface SearchIndexPort {

    String buildIndex(TenantId tenantId, long kbId, long profileId, String alias);

    void upsertChunks(TenantId tenantId, List<Map<String, Object>> chunks);

    List<String> search(TenantId tenantId, String query,
                        List<Long> allowedDocumentIds, int limit);

    void switchAlias(String alias, String physicalName);

    void deleteByVersion(TenantId tenantId, long versionId);
}
