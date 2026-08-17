package com.ragkb.service.modules.identity.persistence.query;

/**
 * {@code tenant_member} 关联 {@code sys_tenant} + {@code tenant_member_role} 的查询行。
 *
 * <p>每行对应一个角色；无角色的成员 {@code role} 为 null。由身份目录聚合为
 * {@code IdentityDirectory.TenantMembership}（一个租户一行，roles 合并）。非实体，仅供查询读取。
 */
public class TenantMembershipRow {

    private Long tenantId;

    private String tenantCode;

    private String tenantName;

    private Long policyVersion;

    private String role;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public Long getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Long policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
