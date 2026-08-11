package com.ragkb.service.modules.analytics.vo;

/**
 * 知识库健康度响应视图。
 */
public record KnowledgeHealthPointVo(
        double noAnswerRate,
        double lowConfRate,
        double averageConfidence,
        double freshnessScore) {
}
