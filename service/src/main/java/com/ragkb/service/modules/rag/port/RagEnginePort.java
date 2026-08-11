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
 * {@code http://rag-engine.ragkb.svc:8000}。
 *
 * <p>⚠️ 具体 HTTP 对接由人工实现（见 {@link RagEngineHttpClient}）。
 * 请求/响应 payload 此处用 {@code Map} 透传，避免与 Python 侧结构过早耦合；
 * 人工实现时按 YAML 契约定义正式 DTO。
 */
public interface RagEnginePort {

    /** POST /api/ingest/documents：触发解析（异步），返回 taskId。 */
    String parseDocument(TenantId tenantId, long documentId, long versionNo,
                         String objectKey, Map<String, Object> kbConfig);

    /** GET /api/ingest/tasks/{id}：查询解析任务状态。 */
    Map<String, Object> getIngestTaskStatus(TenantId tenantId, String taskId);

    /** POST /api/ingest/delete：删除文档向量（幂等），返回删除数。 */
    int deleteVectors(TenantId tenantId, long documentId, Long versionNo);

    /** POST /api/query/chat：混合检索问答（SSE），token 逐段回调。 */
    void chatStream(TenantId tenantId, Map<String, Object> request, Consumer<String> onToken);

    /** POST /api/query/search：全文搜索（BM25 + 向量融合）。 */
    Map<String, Object> search(TenantId tenantId, Map<String, Object> query);

    /** POST /api/query/rerank：精排 Reranker，返回按分数降序的 chunkId 列表。 */
    List<String> rerank(TenantId tenantId, String query, List<Map<String, String>> candidates, int topN);

    /** GET /api/engine/health：引擎健康探针。 */
    Map<String, Object> health();

    /** POST /api/engine/route-status：探测模型可用性（路由决策）。 */
    Map<String, Object> routeStatus(String routeType, String modelName);
}
