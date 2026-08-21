package com.ragkb.service.modules.rag.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.rag.port.ChatStreamEvent;
import com.ragkb.service.modules.rag.port.RagEnginePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * rag-engine（Python）HTTP 适配器——问答与全文搜索闭环的真实对接。
 *
 * <p>简单 JSON 调用用 {@link RestClient}；SSE 问答用 JDK {@link HttpClient}
 * 逐行解析 {@code event:/data:}（Python 侧按单行 JSON 编码，避免换行破坏边界）。
 *
 * <p>超时：普通调用默认 {@code rag-engine.timeout-ms}（10s）；chat 流默认
 * {@code rag-engine.chat-timeout-ms}（120s，LLM 流式首个事件等待上限，正文可长）。
 *
 * <p>2026-08-17 全文搜索链路打通：{@link #search} / {@link #rerank} /
 * {@link #getChunk} 从桩替换为真实调用（search 走 page 分页由调用方换算 cursor）。
 */
@Component
public class RagEngineHttpClient implements RagEnginePort {

    private static final Logger log = LoggerFactory.getLogger(RagEngineHttpClient.class);

    private final String baseUrl;
    private final long timeoutMs;
    private final long chatTimeoutMs;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RagEngineHttpClient(
            @Value("${rag-engine.base-url:http://localhost:8000}") String baseUrl,
            @Value("${rag-engine.timeout-ms:10000}") long timeoutMs,
            @Value("${rag-engine.chat-timeout-ms:120000}") long chatTimeoutMs,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.chatTimeoutMs = chatTimeoutMs;
        this.objectMapper = objectMapper;
        // 内网直连 rag-engine：显式禁用代理。IDE/系统代理（http.proxyHost 系统属性）
        // 会劫持 localhost 调用（Clash 拒绝 → DISPATCH_FAILED），ProxySelector.of(null) 表示直连。
        this.httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String parseDocument(TenantId tenantId, long documentId, long versionId, long kbId,
                                long versionNo, String objectKey, Map<String, Object> kbConfig) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId.value());
        body.put("documentId", documentId);
        body.put("versionId", versionId);
        body.put("kbId", kbId);
        body.put("versionNo", versionNo);
        body.put("objectKey", objectKey);
        body.put("kbConfig", kbConfig == null ? RagEnginePort.defaultKbConfig() : kbConfig);
        String json = restClient.post()
                .uri("/api/ingest/documents")
                .body(body)
                .retrieve()
                .body(String.class);
        Map<String, Object> result = parseJsonObject(json);
        Object taskId = result.get("taskId");
        if (taskId == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "rag-engine 摄取响应缺少 taskId: " + (json == null ? "null" : json));
        }
        log.info("rag-engine ingest submitted: documentId={} versionId={} taskId={}", documentId, versionId, taskId);
        return String.valueOf(taskId);
    }

    @Override
    public Map<String, Object> getIngestTaskStatus(TenantId tenantId, String taskId) {
        String json = restClient.get()
                .uri("/api/ingest/tasks/{taskId}", taskId)
                .retrieve()
                .body(String.class);
        return parseJsonObject(json);
    }

    @Override
    public int deleteVectors(TenantId tenantId, long documentId, Long versionNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentId", documentId);
        if (versionNo != null) {
            body.put("versionNo", versionNo);
        }
        String json = restClient.post()
                .uri("/api/ingest/delete")
                .body(body)
                .retrieve()
                .body(String.class);
        Map<String, Object> result = parseJsonObject(json);
        return result.get("deletedCount") instanceof Number n ? n.intValue() : 0;
    }

    @Override
    public void chatStream(TenantId tenantId, Map<String, Object> request, Consumer<ChatStreamEvent> onEvent) {
        request.put("tenantId", tenantId.value());
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "rag-engine 问答请求序列化失败", e);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/query/chat"))
                // 显式 HTTP/1.1：JDK HttpClient 默认发送 h2c Upgrade 头（Connection: Upgrade,
                // HTTP2-Settings），uvicorn/h11 拒绝「带 body 的 POST + Upgrade」返回 400
                // "Invalid HTTP request received."。SSE 流式本就基于 HTTP/1.1 chunked。
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofMillis(chatTimeoutMs))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
        try {
            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR,
                        "rag-engine chat 返回 " + response.statusCode());
            }
            String[] currentEvent = {null};
            response.body().forEach(line -> {
                if (line.startsWith("event: ")) {
                    currentEvent[0] = line.substring("event: ".length()).trim();
                } else if (line.startsWith("data: ")) {
                    String raw = line.substring("data: ".length()).trim();
                    if (raw.isEmpty()) {
                        return;
                    }
                    Object payload = parseJson(raw);
                    onEvent.accept(new ChatStreamEvent(
                            currentEvent[0] == null ? "data" : currentEvent[0], payload));
                    currentEvent[0] = null; // 一条事件消费后复位，防止重复挂名
                }
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "rag-engine chat 连接失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> search(TenantId tenantId, Map<String, Object> query) {
        // 租户上下文统一在此注入（与 chatStream 同一约定），调用方只组装业务参数。
        query.put("tenantId", tenantId.value());
        String json = restClient.post()
                .uri("/api/query/search")
                .body(query)
                .retrieve()
                .body(String.class);
        return parseJsonObject(json);
    }

    @Override
    public List<String> rerank(TenantId tenantId, String query, List<Map<String, String>> candidates, int topN) {
        // Python 侧为确定性词项覆盖精排：query + candidates(chunkId/text) → topN。
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("query", query);
        body.put("topN", topN);
        body.put("candidates", candidates);
        String json = restClient.post()
                .uri("/api/query/rerank")
                .body(body)
                .retrieve()
                .body(String.class);
        // 解析信封 items[].chunkId → 按分数降序的 chunkId 列表。
        Object items = parseJsonObject(json).get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<String> chunkIds = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("chunkId") instanceof String chunkId) {
                chunkIds.add(chunkId);
            }
        }
        return chunkIds;
    }

    @Override
    public Map<String, Object> getChunk(TenantId tenantId, String chunkId) {
        // GET 无请求体，租户经 query 参数透传（内网信任域，与 search 同级）。
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/query/hits/{chunkId}")
                        .queryParam("tenantId", tenantId.value())
                        .build(chunkId))
                .retrieve()
                .body(String.class);
        return parseJsonObject(json);
    }

    @Override
    public Map<String, Object> health() {
        String json = restClient.get().uri("/api/engine/health").retrieve().body(String.class);
        return parseJsonObject(json);
    }

    @Override
    public Map<String, Object> routeStatus(String routeType, String modelName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("routeType", routeType);
        body.put("modelName", modelName);
        String json = restClient.post().uri("/api/engine/route-status").body(body)
                .retrieve().body(String.class);
        return parseJsonObject(json);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.convertValue(parseJson(json), new TypeReference<Map<String, Object>>() {
        });
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "rag-engine 响应解析失败", e);
        }
    }
}
