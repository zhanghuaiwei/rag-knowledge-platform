package com.ragkb.service.modules.identity.persistence.query;

/**
 * 租户成员角色查询行（{@code tenant_member_role}）。
 *
 * <p>每行一个角色；同一成员多行由服务层聚合为 {@code roles} 列表。非实体，仅供查询读取。
 */
public class UserRoleRow {

    private Long userId;

    private String role;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
