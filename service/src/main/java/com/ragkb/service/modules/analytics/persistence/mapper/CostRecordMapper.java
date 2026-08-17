package com.ragkb.service.modules.analytics.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.analytics.persistence.entity.CostRecord;
import com.ragkb.service.modules.analytics.persistence.query.DailyCostBucketRow;
import com.ragkb.service.modules.analytics.persistence.query.ModelCostRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * {@code cost_record} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/CostRecordMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 *
 * <p>⚠️ 模板：复制本接口改名即可（{@code @MapperScan} 已覆盖本包，{@code @Mapper} 可省略，
 * 保留以便单独使用）。
 *
 * <p>聚合统计：按模型 / 按时间桶聚合 Token 与成本，均限定 tenant_id + del_flag=0，
 * 聚合维度（bucket/tz）由服务层白名单校验后传入。
 */
@Mapper
public interface CostRecordMapper extends BaseMapper<CostRecord> {

    /** 按模型聚合 Token 与成本（getTokenCosts 事实源；表空时返回空列表）。 */
    List<ModelCostRow> selectModelCosts(@Param("tenantId") long tenantId,
                                        @Param("since") Instant since);

    /** 按时间桶聚合全场景成本（getDailyUsage 合并每日真实成本用）。 */
    List<DailyCostBucketRow> selectDailyCosts(@Param("tenantId") long tenantId,
                                              @Param("since") Instant since,
                                              @Param("bucket") String bucket,
                                              @Param("tz") String tz);
}
