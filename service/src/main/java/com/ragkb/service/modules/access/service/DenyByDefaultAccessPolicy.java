package com.ragkb.service.modules.access.service;

import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.UserId;
import com.ragkb.service.modules.access.domain.AccessDecision;
import com.ragkb.service.modules.access.domain.DocumentPermission;
import com.ragkb.service.modules.access.domain.SubjectContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认拒绝回退（db.enabled=false 时的脚手架策略）。
 *
 * <p>数据源（kb_member/document_acl）不可用时，资源授权一律拒绝
 * （认证授权：Redis/数据源故障默认拒绝，不静默放行）。启用数据库后由
 * {@link AccessPolicyServiceImpl} 接管。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "false", matchIfMissing = true)
public class DenyByDefaultAccessPolicy implements AccessPolicyUseCase {

    @Override
    public AccessDecision decideDocument(SubjectContext subject, long documentId, DocumentPermission requested) {
        return AccessDecision.deny("POLICY_SOURCE_UNAVAILABLE", subject.policyVersion());
    }

    @Override
    public boolean canViewExcerpt(TenantId tenantId, UserId userId, long documentId) {
        return false;
    }

    @Override
    public boolean canViewContent(TenantId tenantId, UserId userId, long documentId) {
        return false;
    }

    @Override
    public boolean canDownloadOriginal(TenantId tenantId, UserId userId, long documentId) {
        return false;
    }
}
