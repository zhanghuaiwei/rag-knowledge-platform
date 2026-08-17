package com.ragkb.service.modules.admin.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.admin.persistence.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code sys_tenant} 表 Mapper。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/SysTenantMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
