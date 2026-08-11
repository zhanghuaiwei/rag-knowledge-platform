package com.ragkb.service.interfaces.dto;

/**
 * 用量与质量域 DTO（对齐前端 Analytics 契约）。
 *
 * <p>说明：OpenAPI 草案的 UsageReport/CostReport 为聚合视图，前端需要按日明细
 * 渲染图表，故此处直接提供明细/维度点，作为产品契约所需新增端点。
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record UsagePoint(
            String date,
            long searchCount,
            long qaCount,
            long noAnswerCount,
            long lowConfCount,
            long tokenIn,
            long tokenOut,
            long activeUsers,
            double cost) {
    }

    public record TokenCostPoint(
            String modelName,
            long tokenIn,
            long tokenOut,
            double cost,
            long calls) {
    }

    public record TopDocumentPoint(
            long documentId,
            String fileName,
            String kbName,
            long qaCount,
            long searchCount) {
    }

    public record DauPoint(String date, long activeUsers) {
    }

    public record KnowledgeHealthPoint(
            double noAnswerRate,
            double lowConfRate,
            double averageConfidence,
            double freshnessScore) {
    }
}
