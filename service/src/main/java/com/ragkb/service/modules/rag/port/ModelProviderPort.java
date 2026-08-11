package com.ragkb.service.modules.rag.port;

import com.ragkb.service.common.model.TenantId;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 模型服务端口：Embedding / Rerank / LLM。
 * 路由策略（sensitivity / region / purpose / budget / health）由领域层校验，
 * 不合规 provider 不进入 fallback 集合（05-技术选型 §3.7）。
 */
public interface ModelProviderPort {

    List<List<Float>> embed(TenantId tenantId, List<String> texts, String model);

    List<Float> rerank(TenantId tenantId, String query,
                       List<String> candidates, String model, int topN);

    /** 流式问答：实现内部将 token 逐段交给 onToken 回调。 */
    void chatStream(TenantId tenantId, List<Map<String, String>> messages,
                    String model, Consumer<String> onToken);
}
