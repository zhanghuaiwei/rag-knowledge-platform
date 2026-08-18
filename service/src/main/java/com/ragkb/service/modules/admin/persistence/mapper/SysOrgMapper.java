package com.ragkb.service.modules.admin.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.admin.persistence.entity.SysOrg;
import com.ragkb.service.modules.admin.persistence.query.OrgMemberCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code sys_org} 表 Mapper：单表 CRUD 走 {@link BaseMapper}，
 * 聚合计数 / 子树路径重写 / 物理删除等自定义 SQL 见
 * {@code resources/mapper/SysOrgMapper.xml}（手写 SQL 显式带 tenant_id + del_flag = 0）。
 */
@Mapper
public interface SysOrgMapper extends BaseMapper<SysOrg> {

    /**
     * 按组织聚合成员数（{@code sys_user_org} GROUP BY org_id）：
     * 组织列表一次查询回填 memberCount，避免逐组织 N+1 计数。
     */
    List<OrgMemberCountRow> selectMemberCounts(@Param("tenantId") long tenantId);

    /**
     * 物理删除组织行：{@code uq_sys_org_sibling_name} 唯一约束不含 del_flag，
     * 软删会占住同级名称导致同名组织无法重建（对齐 identity 模块 hardDelete 的处理理由）。
     * 删除守卫（无成员/无子组织）由服务层前置校验。
     */
    int hardDeleteById(@Param("tenantId") long tenantId, @Param("orgId") long orgId);

    /**
     * 组织移动后整棵子树的物化路径前缀重写：
     * {@code path LIKE oldPrefix%} 命中含自身的全部后代，一次 SQL 完成子树迁移
     * （子孙 path 均以祖先路径为前缀，前缀替换即可保持一致）。
     */
    int replaceSubtreePath(@Param("tenantId") long tenantId,
                           @Param("oldPrefix") String oldPrefix,
                           @Param("newPrefix") String newPrefix);
}
