package com.ragkb.service.adapters.ragengine;

import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.common.TenantId;
import com.ragkb.service.ports.RagEnginePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * rag-engine（Python）HTTP 适配器——桩实现。
 *
 * <p>⚠️ 具体对接由人工实现，按 {@code docs/api/rag-engine.openapi.yaml}：
 * <ol>
 *   <li>基于 {@code rag-engine.base-url} 用 RestClient 发起调用（集群内网，无用户级鉴权，
 *       但需携带 RetrievalAccessContext 短期签名上下文）。</li>
 *   <li>配 connect/read/overall timeout、重试与熔断（06-架构方案：外部调用约束）。</li>
 *   <li>SEA/chat 走 SSE 流式（{@link #chatStream}），token 逐段回调。</li>
 * </ol>
 */
@Component
public class RagEngineHttpClient implements RagEnginePort {

    /** rag-engine 集群内网地址（06-架构方案）。 */
    @Value("${rag-engine.base-url:http://rag-engine.ragkb.svc:8000}")
    private String baseUrl;

    @Value("${rag-engine.timeout-ms:10000}")
    private long timeoutMs;

    @Override
    public String parseDocument(TenantId tenantId, long documentId, long versionNo,
                                String objectKey, Map<String, Object> kbConfig) {
        return NotYetImplemented.stub("RagEngineHttpClient#parseDocument (POST /api/ingest/documents)");
    }

    @Override
    public Map<String, Object> getIngestTaskStatus(TenantId tenantId, String taskId) {
        return NotYetImplemented.stub("RagEngineHttpClient#getIngestTaskStatus (GET /api/ingest/tasks/{id})");
    }

    @Override
    public int deleteVectors(TenantId tenantId, long documentId, Long versionNo) {
        return NotYetImplemented.stub("RagEngineHttpClient#deleteVectors (POST /api/ingest/delete)");
    }

    @Override
    public void chatStream(TenantId tenantId, Map<String, Object> request, Consumer<String> onToken) {
        NotYetImplemented.stub("RagEngineHttpClient#chatStream (POST /api/query/chat, SSE)");
    }

    @Override
    public Map<String, Object> search(TenantId tenantId, Map<String, Object> query) {
        return NotYetImplemented.stub("RagEngineHttpClient#search (POST /api/query/search)");
    }

    @Override
    public List<String> rerank(TenantId tenantId, String query, List<Map<String, String>> candidates, int topN) {
        return NotYetImplemented.stub("RagEngineHttpClient#rerank (POST /api/query/rerank)");
    }

    @Override
    public Map<String, Object> health() {
        return NotYetImplemented.stub("RagEngineHttpClient#health (GET /api/engine/health)");
    }

    @Override
    public Map<String, Object> routeStatus(String routeType, String modelName) {
        return NotYetImplemented.stub("RagEngineHttpClient#routeStatus (POST /api/engine/route-status)");
    }
}
