package com.ragkb.service.modules.analytics.persistence.query;

import java.math.BigDecimal;

/**
 * 问答质量单行汇总（事实源：{@code chat_message}，近 N 天 ASSISTANT 回答消息的总计）。
 *
 * <p>供 {@code getKnowledgeHealth} 计算无答案率 / 低置信率 / 平均置信度；
 * 分子分母同源同窗口，服务层只做除法与空值兜底。
 */
public class AnswerQualityRow {

    /** 回答消息总数（分母；0 表示窗口内无问答，各比率兜底为 0）。 */
    private Long totalAnswers;

    /** answer_status = NO_ANSWER 的回答数。 */
    private Long noAnswerCount;

    /** answer_status = LOW_CONFIDENCE 的回答数。 */
    private Long lowConfCount;

    /** 平均置信度 AVG(confidence)（窗口内无回答时为 null，服务层兜底为 0）。 */
    private BigDecimal avgConfidence;

    /** 读取回答总数。 */
    public Long getTotalAnswers() {
        return totalAnswers;
    }

    /** 设置回答总数（SQL 聚合结果回填）。 */
    public void setTotalAnswers(Long totalAnswers) {
        this.totalAnswers = totalAnswers;
    }

    /** 读取无答案数。 */
    public Long getNoAnswerCount() {
        return noAnswerCount;
    }

    /** 设置无答案数（SQL 聚合结果回填）。 */
    public void setNoAnswerCount(Long noAnswerCount) {
        this.noAnswerCount = noAnswerCount;
    }

    /** 读取低置信数。 */
    public Long getLowConfCount() {
        return lowConfCount;
    }

    /** 设置低置信数（SQL 聚合结果回填）。 */
    public void setLowConfCount(Long lowConfCount) {
        this.lowConfCount = lowConfCount;
    }

    /** 读取平均置信度。 */
    public BigDecimal getAvgConfidence() {
        return avgConfidence;
    }

    /** 设置平均置信度（SQL 聚合结果回填）。 */
    public void setAvgConfidence(BigDecimal avgConfidence) {
        this.avgConfidence = avgConfidence;
    }
}
