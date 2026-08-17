package com.ragkb.service.modules.identity.persistence.query;

/**
 * 租户成员所属组织查询行（{@code sys_user_org} JOIN {@code sys_org}）。
 *
 * <p>每行一个（成员, 组织）；同名组织可能多行（m2m 设计），服务层取去重后的第一个用于
 * {@code UserVo.orgName} 展示。非实体，仅供查询读取。
 */
public class UserOrgRow {

    private Long userId;

    private String orgName;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }
}
