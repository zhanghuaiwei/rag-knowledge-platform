package com.ragkb.service.modules.admin.persistence.query;

/**
 * 组织成员计数聚合行（{@code sys_user_org} 按 {@code org_id} GROUP BY 的结果，
 * 供组织列表一次聚合各节点成员数，避免逐组织 N+1 计数）。
 */
public class OrgMemberCountRow {

    /** 组织 id（GROUP BY 键）。 */
    private Long orgId;

    /** 该组织挂靠的成员数（COUNT(*)）。 */
    private Long memberCount;

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Long memberCount) {
        this.memberCount = memberCount;
    }
}
