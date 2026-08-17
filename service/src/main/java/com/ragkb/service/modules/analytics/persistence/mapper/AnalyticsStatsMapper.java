package com.ragkb.service.modules.analytics.persistence.mapper;

import com.ragkb.service.modules.analytics.persistence.query.ActiveUserBucketRow;
import com.ragkb.service.modules.analytics.persistence.query.AnswerQualityRow;
import com.ragkb.service.modules.analytics.persistence.query.DocFreshnessRow;
import com.ragkb.service.modules.analytics.persistence.query.TopDocumentRow;
import com.ragkb.service.modules.analytics.persistence.query.UsageBucketRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * analytics 模块自有「读模型」Mapper：跨表统计聚合的唯口子。
 *
 * <p>定位（模块边界约定）：问答/文档等业务表的原表 CRUD 归属各自模块的 Mapper；
 * analytics 只做只读聚合，SQL 统一收口在本接口与
 * {@code resources/mapper/AnalyticsStatsMapper.xml}，<b>不反向散落到 conversation /
 * document 等模块</b>（PackageStructureTest 红线：跨模块只经 Service/Port 协作）。
 *
 * <p>安全约定：
 * <ul>
 *   <li>所有查询显式 {@code tenant_id} 过滤（多租户隔离）+ {@code del_flag = 0}
 *       （{@code @TableLogic} 不作用于手写 XML）；</li>
 *   <li>{@code bucket}/{@code tz} 等动态片段由服务层白名单校验后传入，无注入面。</li>
 * </ul>
 */
@Mapper
public interface AnalyticsStatsMapper {

    /** 按时间桶聚合问答用量（仅 ASSISTANT 回答消息计一次问答；date_trunc 按业务时区）。 */
    List<UsageBucketRow> selectUsageBuckets(@Param("tenantId") long tenantId,
                                            @Param("since") Instant since,
                                            @Param("bucket") String bucket,
                                            @Param("tz") String tz);

    /** 按时间桶统计去重活跃用户数（chat_message JOIN chat_session 取 user_id）。 */
    List<ActiveUserBucketRow> selectActiveUserBuckets(@Param("tenantId") long tenantId,
                                                      @Param("since") Instant since,
                                                      @Param("bucket") String bucket,
                                                      @Param("tz") String tz);

    /** 问答质量单行汇总（近 N 天回答总数 / 无答案 / 低置信 / 平均置信度）。 */
    AnswerQualityRow selectAnswerQuality(@Param("tenantId") long tenantId,
                                         @Param("since") Instant since);

    /** 热门文档聚合：近 N 天被回答引用最多的文档 TOP N，带文档名/知识库名。 */
    List<TopDocumentRow> selectTopDocuments(@Param("tenantId") long tenantId,
                                            @Param("since") Instant since,
                                            @Param("limit") int limit);

    /** 在库（ACTIVE）文档总数与近 N 天更新数（freshnessScore 事实源）。 */
    DocFreshnessRow selectDocFreshness(@Param("tenantId") long tenantId,
                                       @Param("freshSince") Instant freshSince);
}
