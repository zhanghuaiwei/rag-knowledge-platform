package com.ragkb.service.modules.access.domain;

/**
 * 资源授权决策结果（认证授权 §5.2）：allow/deny + reasonCode + policyVersion。
 * 前端与审计可据此区分"文档不存在/未发布/无权限"等拒绝原因（不泄漏存在性信息由调用方把握）。
 */
public record AccessDecision(boolean allow, String reasonCode, long policyVersion) {

    public static AccessDecision allow(long policyVersion) {
        return new AccessDecision(true, null, policyVersion);
    }

    public static AccessDecision deny(String reasonCode, long policyVersion) {
        return new AccessDecision(false, reasonCode, policyVersion);
    }
}
