package com.ragkb.service.modules.document.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.document.persistence.entity.DocumentTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code document_tag} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/DocumentTagMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 *
 * <p>⚠️ 模板：复制本接口改名即可（{@code @MapperScan} 已覆盖本包，{@code @Mapper} 可省略，
 * 保留以便单独使用）。
 */
@Mapper
public interface DocumentTagMapper extends BaseMapper<DocumentTag> {
}
