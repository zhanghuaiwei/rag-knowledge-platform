package com.ragkb.service.modules.analytics.persistence.query;

/**
 * 按时间桶（日/周/月）聚合的问答用量行（事实源：{@code chat_message}，仅统计 ASSISTANT 回答消息）。
 *
 * <p>非实体，仅供 {@code AnalyticsServiceImpl.getDailyUsage} 读取聚合结果。
 * {@code searchCount}/{@code cost} 不在本行：搜索行为无事实源表（恒 0），
 * 成本来自 {@code cost_record} 另行按桶聚合后在服务层合并。
 */
public class UsageBucketRow {

    /** 统计桶起始日（业务时区 Asia/Shanghai 下 date_trunc 后的 YYYY-MM-DD）。 */
    private String statDate;

    /** 该桶内回答消息总数（一条 ASSISTANT 消息计一次问答）。 */
    private Long qaCount;

    /** 该桶内 answer_status = NO_ANSWER 的回答数（无答案问答量）。 */
    private Long noAnswerCount;

    /** 该桶内 answer_status = LOW_CONFIDENCE 的回答数（低置信问答量）。 */
    private Long lowConfCount;

    /** 该桶内回答消耗的输入 token 合计（chat_message.token_in 求和）。 */
    private Long tokenIn;

    /** 该桶内回答消耗的输出 token 合计（chat_message.token_out 求和）。 */
    private Long tokenOut;

    /** 读取统计桶起始日。 */
    public String getStatDate() {
        return statDate;
    }

    /** 设置统计桶起始日（SQL 聚合结果回填）。 */
    public void setStatDate(String statDate) {
        this.statDate = statDate;
    }

    /** 读取该桶问答总数。 */
    public Long getQaCount() {
        return qaCount;
    }

    /** 设置该桶问答总数（SQL 聚合结果回填）。 */
    public void setQaCount(Long qaCount) {
        this.qaCount = qaCount;
    }

    /** 读取该桶无答案数。 */
    public Long getNoAnswerCount() {
        return noAnswerCount;
    }

    /** 设置该桶无答案数（SQL 聚合结果回填）。 */
    public void setNoAnswerCount(Long noAnswerCount) {
        this.noAnswerCount = noAnswerCount;
    }

    /** 读取该桶低置信数。 */
    public Long getLowConfCount() {
        return lowConfCount;
    }

    /** 设置该桶低置信数（SQL 聚合结果回填）。 */
    public void setLowConfCount(Long lowConfCount) {
        this.lowConfCount = lowConfCount;
    }

    /** 读取该桶输入 token 合计。 */
    public Long getTokenIn() {
        return tokenIn;
    }

    /** 设置该桶输入 token 合计（SQL 聚合结果回填）。 */
    public void setTokenIn(Long tokenIn) {
        this.tokenIn = tokenIn;
    }

    /** 读取该桶输出 token 合计。 */
    public Long getTokenOut() {
        return tokenOut;
    }

    /** 设置该桶输出 token 合计（SQL 聚合结果回填）。 */
    public void setTokenOut(Long tokenOut) {
        this.tokenOut = tokenOut;
    }
}
