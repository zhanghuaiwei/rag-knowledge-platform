package com.ragkb.service.modules.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.identity.persistence.entity.UserCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code user_credential} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/UserCredentialMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 *
 * <p>⚠️ 登录标识按 {@code lower(username)} 匹配（见 {@code deploy/ddl/migrations/V0.4__local_user_credentials.sql} 唯一索引），
 * 业务实现时用 {@code LambdaQueryWrapper.eq(UserCredential::getUsername, username)} 即可（SQL 层 lower 归一，
 * 大小写不敏感由唯一索引约束保证唯一性）。
 */
@Mapper
public interface UserCredentialMapper extends BaseMapper<UserCredential> {
}
