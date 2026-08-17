package com.ragkb.service.modules.analytics.persistence.query;

/**
 * 文档新鲜度计数行（事实源：{@code document}，仅 lifecycle_status = ACTIVE 的在库文档）。
 *
 * <p>freshnessScore = freshDocs / totalDocs：近 90 天内有更新（update_time 活跃）
 * 的在库文档占比，衡量知识库内容是否陈旧。
 */
public class DocFreshnessRow {

    /** 在库文档总数（ACTIVE 且未逻辑删除）。 */
    private Long totalDocs;

    /** 近 90 天内更新过的在库文档数。 */
    private Long freshDocs;

    /** 读取在库文档总数。 */
    public Long getTotalDocs() {
        return totalDocs;
    }

    /** 设置在库文档总数（SQL 聚合结果回填）。 */
    public void setTotalDocs(Long totalDocs) {
        this.totalDocs = totalDocs;
    }

    /** 读取近 90 天更新文档数。 */
    public Long getFreshDocs() {
        return freshDocs;
    }

    /** 设置近 90 天更新文档数（SQL 聚合结果回填）。 */
    public void setFreshDocs(Long freshDocs) {
        this.freshDocs = freshDocs;
    }
}
