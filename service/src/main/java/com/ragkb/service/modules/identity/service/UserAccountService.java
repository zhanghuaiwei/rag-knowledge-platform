package com.ragkb.service.modules.identity.service;

import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.identity.dto.CreateLocalUserRequest;
import com.ragkb.service.modules.identity.vo.UserVo;

import java.util.List;

/**
 * 租户成员账号管理用例（管理中心用户管理）。
 *
 * <p>作用域：当前激活租户（{@code JwtPrincipal.tenantId()}），REST 路径由
 * {@code UserAccountController} 以 {@code tenant-member:manage} 权限码门禁（仅 TENANT_ADMIN）。
 * 用户为全局身份（{@code sys_user}），租户关系在 {@code tenant_member / tenant_member_role}。
 *
 * <p>⚠️ 谨慎区（人工实现）与机械逻辑分工：{@code listUsers/disableUser/enableUser} 由 AI 实现；
 * {@code createLocalUser/setRoles/removeFromTenant/resetPassword} 为人工实现点
 * （单事务、密码操作、最后管理员/自我操作守卫等安全敏感逻辑）。
 * 组织调整（{@code updateUserOrg}）因 {@code sys_org/sys_user_org} 属 admin 模块持久化，
 * 跨模块铁律下保留在 {@code AdminService}（admin 模块）延后实现。
 */
public interface UserAccountService {

    /** 租户成员分页列表（roles 聚合为多角色；SUSPENDED 映射为 DISABLED 展示）。 */
    PageData<UserVo> listUsers(int page, int size);

    /**
     * 创建本地用户：单事务插入 {@code sys_user + user_credential + tenant_member + tenant_member_role}。
     *
     * <p>⚠️ 谨慎区（人工实现）：初始密码 BCrypt 编码；{@code must_change_password=true}、
     * {@code password_expires_at=now+expiryDays}；用户名冲突捕获部分唯一索引
     * {@code DuplicateKeyException} → {@code ApiException(CONFLICT, "登录账号已存在")}。
     */
    UserVo createLocalUser(CreateLocalUserRequest request);

    /** 停用成员（租户成员状态 SUSPENDED，撤权）；返回更新后视图。 */
    UserVo disableUser(long userId);

    /** 重新启用成员（租户成员状态 ACTIVE）；返回更新后视图。 */
    UserVo enableUser(long userId);

    /**
     * 调整成员所属组织（本次延后：骨架）。
     *
     * <p>⚠️ 谨慎区（人工实现）：{@code sys_user_org} 为 m2m，当前契约为 clear-and-set 单 org
     * （orgId 为 null 表示移出组织）。组织表属 admin 模块持久化，经
     * {@code UserAccountMapper}（identity 模块 XML 直连 SQL）读写，避免跨模块 Java 依赖。
     */
    UserVo updateUserOrg(long userId, Long orgId);

    /**
     * 覆盖式替换租户角色（整体替换，非增量）。
     *
     * <p>⚠️ 谨慎区（人工实现）：删除多余角色行 + 插入缺失角色行（同一事务）；
     * 守卫：不得移除当前租户最后一名 {@code TENANT_ADMIN}、不得修改自己的角色。
     */
    UserVo setRoles(long userId, List<String> roles);

    /**
     * 移出当前租户：物理删除 {@code tenant_member}（级联角色/组织关联），全局身份保留。
     *
     * <p>⚠️ 谨慎区（人工实现）：守卫：不得移出最后一名 {@code TENANT_ADMIN}、不得移出自己；
     * 写 {@code audit_log}。规则 18 解读：关系行清理，非删除用户数据。
     */
    void removeFromTenant(long userId);

    /**
     * 管理员重置密码：新密码 BCrypt 编码后写入凭据，置 {@code must_change_password=true}。
     *
     * <p>⚠️ 谨慎区（人工实现）：可选吊销该用户 refresh 家族；写 {@code audit_log}。
     */
    void resetPassword(long userId, String newPassword);
}
