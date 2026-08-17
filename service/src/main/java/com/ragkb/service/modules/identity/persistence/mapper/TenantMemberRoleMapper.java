package com.ragkb.service.modules.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.identity.persistence.entity.TenantMemberRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code tenant_member_role} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/TenantMemberRoleMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 *
 * <p>⚠️ 模板：复制本接口改名即可（{@code @MapperScan} 已覆盖本包，{@code @Mapper} 可省略，
 * 保留以便单独使用）。
 */
@Mapper
public interface TenantMemberRoleMapper extends BaseMapper<TenantMemberRole> {

    /**
     * 物理删除成员在指定租户的全部角色行（setRoles 覆盖式替换的清空步）。
     *
     * <p>⚠️ 有意绕过 {@code @TableLogic}（默认 delete 是逻辑删除）：表上的
     * {@code uq_tenant_member_role (tenant_id,user_id,role)} 是全表唯一约束（不含 del_flag 条件），
     * 逻辑删除行仍占用唯一键，会导致后续重新插入同角色撞约束。角色行是纯关系数据，
     * 生命周期完全跟随成员关系（removeTenant 经 FK CASCADE 也是物理删），物理删除安全。
     */
    @Delete("DELETE FROM tenant_member_role WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    int hardDeleteByTenantAndUser(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
