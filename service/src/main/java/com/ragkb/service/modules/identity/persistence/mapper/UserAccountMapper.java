package com.ragkb.service.modules.identity.persistence.mapper;

import com.ragkb.service.modules.identity.persistence.query.UserAccountRow;
import com.ragkb.service.modules.identity.persistence.query.UserOrgRow;
import com.ragkb.service.modules.identity.persistence.query.UserRoleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租户成员账号只读查询 Mapper（管理中心用户列表）。
 *
 * <p>单表 CRUD 由各实体 {@code BaseMapper} 提供；本接口做跨表 JOIN 的只读读取
 * （{@code tenant_member} × {@code sys_user} × {@code user_credential} ×
 * {@code tenant_member_role} × {@code sys_user_org}/{@code sys_org}），
 * 全部按 {@code tenant_id} 限定当前租户，且手写 {@code del_flag = 0} 过滤
 * （MyBatis-Plus 的 {@code @TableLogic} 只作用于生成 CRUD，不作用于手写 XML）。
 *
 * <p>实现见 {@code resources/mapper/UserAccountMapper.xml}。
 */
@Mapper
public interface UserAccountMapper {

    /** 租户成员分页行（不含角色/组织，避免笛卡尔积；角色与组织另行批量查询）。 */
    List<UserAccountRow> selectMemberPage(@Param("tenantId") long tenantId,
                                          @Param("offset") long offset,
                                          @Param("size") int size);

    /** 租户内指定成员行（非分页，IN 查询；供变更后单用户重读组装 UserVo）。 */
    List<UserAccountRow> selectMembers(@Param("tenantId") long tenantId,
                                       @Param("userIds") List<Long> userIds);

    /** 租户内若干成员的全部角色（每行一个角色，供服务层聚合为 roles 列表）。 */
    List<UserRoleRow> selectMemberRoles(@Param("tenantId") long tenantId,
                                        @Param("userIds") List<Long> userIds);

    /** 租户内若干成员的所属组织名（m2m 去重后取展示用）。 */
    List<UserOrgRow> selectMemberOrgs(@Param("tenantId") long tenantId,
                                      @Param("userIds") List<Long> userIds);

    /** 租户成员总数（逻辑未删除）。 */
    long countMembers(@Param("tenantId") long tenantId);

    // ---- 组织关联写操作（sys_user_org / sys_org 属 admin 模块持久化，本处经 SQL 直连，避免跨模块 Java 依赖） ----

    /** 清空成员在当前租户的全部组织关联（clear-and-set 的 clear 步）。 */
    int deleteMemberOrgs(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 为成员在当前租户新增一条组织关联（幂等：撞 (tenant_id,user_id,org_id) 唯一约束时忽略）。 */
    int insertMemberOrg(@Param("tenantId") long tenantId,
                        @Param("userId") long userId,
                        @Param("orgId") long orgId);

    /** 校验组织在当前租户存在且 ACTIVE（clear-and-set 的合法性检查）。 */
    long countOrgInTenant(@Param("tenantId") long tenantId, @Param("orgId") long orgId);

    /**
     * 统计当前租户内「成员状态 ACTIVE 且持有 TENANT_ADMIN 角色」的管理员人数
     * （JOIN tenant_member 过滤 SUSPENDED/逻辑删除成员）。供 setRoles/removeFromTenant
     * 的"最后一名管理员保护"守卫使用：结果 &lt;= 1 时禁止再移除管理员的最后一份角色。
     */
    long countActiveTenantAdmins(@Param("tenantId") long tenantId);

    /**
     * 写安全审计事件（SQL 直连 audit_log，与 sys_org 直连同理：audit_log 实体属 admin
     * 模块持久化，identity 模块不跨 Java 模块依赖）。actor=当前操作者（USER），
     * 仅记录动作与对象 id，绝不落密码明文等敏感值。
     *
     * @return 受影响行数（正常恒为 1，供测试断言）
     */
    int insertAuditLog(@Param("tenantId") long tenantId,
                       @Param("actorId") long actorId,
                       @Param("action") String action,
                       @Param("resourceId") String resourceId);
}
