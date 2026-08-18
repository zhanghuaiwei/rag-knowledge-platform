package com.ragkb.service.modules.rag.port;

import com.ragkb.service.common.model.TenantId;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * rag-engine（Python）对接端口——服务端 ↔ 检索引擎的「口子」。
 *
 * <p>实现对应 {@code docs/api/rag-engine.openapi.yaml} 的 8 个端点：
 * ingest/documents、ingest/tasks、ingest/delete、query/chat、query/search、
 * query/rerank、engine/health、engine/route-status。集群内网调用
 * {@code http://rag-engine.ragkb.svc:8000}（本地开发 {@code http://localhost:8000}）。
 *
 * <p>2026-08-17 最小问答闭环接线：
 * <ul>
 *   <li>{@code parseDocument} 增加 {@code versionId / kbId}（chunk_meta 外键与 kb 过滤需要）；</li>
 *   <li>{@code chatStream} 回调改为 {@link ChatStreamEvent}（按事件类型转发，而非裸 token）。</li>
 * </ul>
 */
public interface RagEnginePort {

    /** 默认知识库 RAG 参数（与 Python 侧 KbConfig 默认一致）；对接方透传给 rag-engine。 */
    static Map<String, Object> defaultKbConfig() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("embeddingModel", "text-embedding-v3");
        config.put("chunkSize", 512);
        config.put("chunkOverlap", 50);
        config.put("topK", 5);
        config.put("rerankerEnabled", false);
        return config;
    }

    /**
     * POST /api/ingest/documents：触发解析（异步），返回 taskId。
     *
     * @param tenantId    租户 id
     * @param documentId  文档 id
     * @param versionId   document_version.id（chunk_meta 外键引用版本）
     * @param kbId        知识库 id
     * @param versionNo   版本号
     * @param objectKey   对象存储 key（MinIO/S3 对象名）
     * @param kbConfig    知识库 RAG 参数（embeddingModel/chunkSize/chunkOverlap/topK/...）
     */
    String parseDocument(TenantId tenantId, long documentId, long versionId, long kbId,
                         long versionNo, String objectKey, Map<String, Object> kbConfig);

    /** GET /api/ingest/tasks/{id}：查询解析任务状态。 */
    Map<String, Object> getIngestTaskStatus(TenantId tenantId, String taskId);

    /** POST /api/ingest/delete：删除文档向量（幂等），返回删除数。 */
    int deleteVectors(TenantId tenantId, long documentId, Long versionNo);

    /** POST /api/query/chat：混合检索问答（SSE），事件逐段回调。 */
    void chatStream(TenantId tenantId, Map<String, Object> request, Consumer<ChatStreamEvent> onEvent);

    /** POST /api/query/search：全文搜索（BM25 + 向量融合）。 */
    Map<String, Object> search(TenantId tenantId, Map<String, Object> query);

    /** POST /api/query/rerank：精排 Reranker，返回按分数降序的 chunkId 列表。 */
    List<String> rerank(TenantId tenantId, String query, List<Map<String, String>> candidates, int topN);

    /**
     * GET /api/query/hits/{chunkId}：按命中 id 回查分块正文（搜索摘录）。
     * 2026-08-17 新增：全文搜索摘录端点需要按 hitId 直查片段，避免「重新检索定位」
     * 在深翻页/关键词变化时找不到目标命中；跨租户按不存在处理（404 语义）。
     */
    Map<String, Object> getChunk(TenantId tenantId, String chunkId);

    /** GET /api/engine/health：引擎健康探针。 */
    Map<String, Object> health();

    /** POST /api/engine/route-status：探测模型可用性（路由决策）。 */
    Map<String, Object> routeStatus(String routeType, String modelName);
}
