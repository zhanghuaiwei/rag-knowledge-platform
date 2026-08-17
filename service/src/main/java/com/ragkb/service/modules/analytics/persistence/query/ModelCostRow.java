package com.ragkb.service.modules.analytics.persistence.query;

import java.math.BigDecimal;

/**
 * 按模型聚合的 Token 与成本行（事实源：{@code cost_record}，GROUP BY model_name）。
 *
 * <p>供 {@code getTokenCosts} 直接映射为 {@code TokenCostPointVo}；
 * cost_record 无数据时查询结果为空列表（不造假）。
 */
public class ModelCostRow {

    /** 模型名（cost_record.model_name，如 gpt-4o-mini / bge-m3）。 */
    private String modelName;

    /** 该模型输入 token 合计（含 EMBEDDING 等场景）。 */
    private Long tokenIn;

    /** 该模型输出 token 合计。 */
    private Long tokenOut;

    /** 该模型成本合计（cost_record.cost 求和）。 */
    private BigDecimal cost;

    /** 该模型计费调用次数（cost_record 行数）。 */
    private Long calls;

    /** 读取模型名。 */
    public String getModelName() {
        return modelName;
    }

    /** 设置模型名（SQL 聚合结果回填）。 */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /** 读取输入 token 合计。 */
    public Long getTokenIn() {
        return tokenIn;
    }

    /** 设置输入 token 合计（SQL 聚合结果回填）。 */
    public void setTokenIn(Long tokenIn) {
        this.tokenIn = tokenIn;
    }

    /** 读取输出 token 合计。 */
    public Long getTokenOut() {
        return tokenOut;
    }

    /** 设置输出 token 合计（SQL 聚合结果回填）。 */
    public void setTokenOut(Long tokenOut) {
        this.tokenOut = tokenOut;
    }

    /** 读取成本合计。 */
    public BigDecimal getCost() {
        return cost;
    }

    /** 设置成本合计（SQL 聚合结果回填）。 */
    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    /** 读取调用次数。 */
    public Long getCalls() {
        return calls;
    }

    /** 设置调用次数（SQL 聚合结果回填）。 */
    public void setCalls(Long calls) {
        this.calls = calls;
    }
}
