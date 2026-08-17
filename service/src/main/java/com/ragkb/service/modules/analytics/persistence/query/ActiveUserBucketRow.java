package com.ragkb.service.modules.analytics.persistence.query;

/**
 * 按时间桶去重的活跃用户行（事实源：{@code chat_message} JOIN {@code chat_session} 取 user_id）。
 *
 * <p>「活跃」定义：该桶内发送过任意消息（USER 或 ASSISTANT）的去重用户数。
 * 供 {@code getDailyUsage}（按桶活跃用户）与 {@code getDau}（按日活跃用户 DAU）共用。
 */
public class ActiveUserBucketRow {

    /** 统计桶起始日（业务时区 Asia/Shanghai 下 date_trunc 后的 YYYY-MM-DD）。 */
    private String statDate;

    /** 该桶内活跃用户数（COUNT(DISTINCT chat_session.user_id)）。 */
    private Long activeUsers;

    /** 读取统计桶起始日。 */
    public String getStatDate() {
        return statDate;
    }

    /** 设置统计桶起始日（SQL 聚合结果回填）。 */
    public void setStatDate(String statDate) {
        this.statDate = statDate;
    }

    /** 读取该桶活跃用户数。 */
    public Long getActiveUsers() {
        return activeUsers;
    }

    /** 设置该桶活跃用户数（SQL 聚合结果回填）。 */
    public void setActiveUsers(Long activeUsers) {
        this.activeUsers = activeUsers;
    }
}
