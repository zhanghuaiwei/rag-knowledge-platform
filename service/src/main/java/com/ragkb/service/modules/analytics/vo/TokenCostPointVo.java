package com.ragkb.service.modules.analytics.vo;

/**
 * 按模型 Token 成本点响应视图。
 */
public record TokenCostPointVo(
        String modelName,
        long tokenIn,
        long tokenOut,
        double cost,
        long calls) {
}
