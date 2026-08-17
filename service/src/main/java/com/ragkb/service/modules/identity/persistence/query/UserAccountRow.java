package com.ragkb.service.modules.identity.persistence.query;

import java.time.Instant;

/**
 * 租户成员列表查询行（{@code tenant_member} JOIN {@code sys_user} + {@code user_credential}）。
 *
 * <p>每行一个成员；角色与组织名由 {@code UserAccountMapper.selectMemberRoles/selectMemberOrgs}
 * 另行返回（避免笛卡尔积），由服务层聚合为 {@code UserVo}。非实体，仅供查询读取。
 */
public class UserAccountRow {

    private Long userId;

    private String displayName;

    private String primaryEmail;

    /** tenant_member.status：INVITED / ACTIVE / SUSPENDED（服务层映射为 UserVo.status）。 */
    private String memberStatus;

    private Instant lastLoginAt;

    /** 本地凭据的 must_change_password；无本地凭据（如 OIDC 用户）为 null。 */
    private Boolean mustChangePassword;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrimaryEmail() {
        return primaryEmail;
    }

    public void setPrimaryEmail(String primaryEmail) {
        this.primaryEmail = primaryEmail;
    }

    public String getMemberStatus() {
        return memberStatus;
    }

    public void setMemberStatus(String memberStatus) {
        this.memberStatus = memberStatus;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }
}
