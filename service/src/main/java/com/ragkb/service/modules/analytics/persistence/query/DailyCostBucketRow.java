package com.ragkb.service.modules.analytics.persistence.query;

import java.math.BigDecimal;

/**
 * 按时间桶聚合的成本行（事实源：{@code cost_record}，含 EMBEDDING/RERANK/LLM/OCR 全场景）。
 *
 * <p>供 {@code getDailyUsage} 合并每日真实成本：usage_daily 预汇总表当前无写入方，
 * 故成本直接从 cost_record 明细按桶求和（有数据则真实、无数据则该桶成本为 0）。
 */
public class DailyCostBucketRow {

    /** 统计桶起始日（业务时区 Asia/Shanghai 下 date_trunc 后的 YYYY-MM-DD）。 */
    private String statDate;

    /** 该桶内全场景成本合计（cost_record.cost 求和，NUMERIC(18,6)）。 */
    private BigDecimal cost;

    /** 读取统计桶起始日。 */
    public String getStatDate() {
        return statDate;
    }

    /** 设置统计桶起始日（SQL 聚合结果回填）。 */
    public void setStatDate(String statDate) {
        this.statDate = statDate;
    }

    /** 读取该桶成本合计。 */
    public BigDecimal getCost() {
        return cost;
    }

    /** 设置该桶成本合计（SQL 聚合结果回填）。 */
    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }
}
