package com.ragkb.service.modules.identity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.identity.persistence.entity.TenantMember;
import com.ragkb.service.modules.identity.persistence.row.TenantMembershipRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code tenant_member} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页；跨表 JOIN（sys_tenant / tenant_member_role）
 * 在 {@code resources/mapper/TenantMemberMapper.xml} 定义，供身份目录组装成员关系。
 */
@Mapper
public interface TenantMemberMapper extends BaseMapper<TenantMember> {

    /**
     * 用户全部 ACTIVE 租户成员关系（关联 sys_tenant + tenant_member_role，每行一个角色）。
     * 只在 {@code sys_tenant.status='ACTIVE'} 且逻辑未删除时返回。由调用方聚合为租户维度。
     */
    List<TenantMembershipRow> selectActiveMembershipRows(@Param("userId") long userId);

    /**
     * 用户在指定租户的成员关系（同上过滤）；非成员/未激活/租户停用返回空列表。
     */
    List<TenantMembershipRow> selectMembershipRows(@Param("userId") long userId, @Param("tenantId") long tenantId);

    /**
     * 物理删除租户成员关系（"移出租户"专用）。经 FK {@code ON DELETE CASCADE}
     * 级联删除 {@code tenant_member_role} / {@code sys_user_org} 关联行。
     *
     * <p>⚠️ 有意绕过 MyBatis-Plus {@code @TableLogic}（默认 delete 是逻辑删除
     * {@code UPDATE del_flag=1}）——本方法是关系行清理，不是删除用户数据：
     * {@code sys_user} / {@code user_credential} 全局身份由调用方保留（规则 18 解读，
     * 见 {@code UserAccountServiceImpl#removeFromTenant} 的谨慎区契约）。返回受影响行数。
     */
    @Delete("DELETE FROM tenant_member WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    int hardDeleteByTenantAndUser(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
