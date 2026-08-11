package com.ragkb.service.modules.analytics.vo;

/**
 * 按日用量点响应视图（对齐前端 Analytics 图表契约；OpenAPI 草案的 UsageReport 为聚合视图，
 * 此处提供按日明细点）。
 */
public record UsagePointVo(
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
