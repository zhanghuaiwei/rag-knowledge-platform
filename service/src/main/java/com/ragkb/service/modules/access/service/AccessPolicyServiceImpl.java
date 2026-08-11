package com.ragkb.service.modules.access.service;

import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.UserId;
import com.ragkb.service.modules.access.domain.AccessDecision;
import com.ragkb.service.modules.access.domain.DocumentPermission;
import com.ragkb.service.modules.access.domain.SubjectContext;
import com.ragkb.service.modules.document.port.DocumentAccessPort;
import com.ragkb.service.modules.knowledge.port.KbAccessPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 文档资源授权决策实现（db.enabled=true 时激活，认证授权 §5.2 分层授权）。
 *
 * <p>判定顺序：文档存在 → 状态（删除/禁用/未发布）→ KB 角色基础能力 → 文档 ACL 提升 →
 * 权限蕴含校验，最终 allow/deny + reasonCode + policyVersion。默认拒绝，未知角色不宽松回退。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class AccessPolicyServiceImpl implements AccessPolicyUseCase {

    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final KbAccessPort kbAccessPort;
    private final DocumentAccessPort documentAccessPort;
    private final PermissionCatalog permissionCatalog;

    public AccessPolicyServiceImpl(KbAccessPort kbAccessPort, DocumentAccessPort documentAccessPort,
                                   PermissionCatalog permissionCatalog) {
        this.kbAccessPort = kbAccessPort;
        this.documentAccessPort = documentAccessPort;
        this.permissionCatalog = permissionCatalog;
    }

    @Override
    public AccessDecision decideDocument(SubjectContext subject, long documentId, DocumentPermission requested) {
        DocumentAccessPort.DocumentAccessView view = documentAccessPort.viewOf(subject.activeTenantId(), documentId)
                .orElse(null);
        if (view == null) {
            return AccessDecision.deny("DOCUMENT_NOT_FOUND", subject.policyVersion());
        }
        long policyVersion = view.policyVersion() != null ? view.policyVersion() : subject.policyVersion();

        // 状态门禁（默认拒绝）：del_flag=1 视为逻辑删除
        if (Integer.valueOf(1).equals(view.delFlag())) {
            return AccessDecision.deny("DOCUMENT_DELETED", policyVersion);
        }
        if (Boolean.TRUE.equals(view.isDisabled())) {
            return AccessDecision.deny("DOCUMENT_DISABLED", policyVersion);
        }
        if (!STATUS_PUBLISHED.equals(view.lifecycleStatus())) {
            return AccessDecision.deny("DOCUMENT_STATUS_" + view.lifecycleStatus(), policyVersion);
        }

        // KB 角色基础能力（无成员关系 → 无基础权限）
        String kbRole = subject.userId() != null
                ? kbAccessPort.roleOf(subject.activeTenantId(), subject.userId(), view.kbId()).orElse(null)
                : null;
        DocumentPermission effective = basePermission(kbRole);

        // 文档 ACL 提升（USER / ORG / TENANT_ROLE / KB_ROLE，取最高匹配档位）
        DocumentPermission aclPermission = maxAclPermission(view.acls(), subject, kbRole);
        if (aclPermission != null && (effective == null || aclPermission.rank() > effective.rank())) {
            effective = aclPermission;
        }

        if (effective == null) {
            return AccessDecision.deny("NO_KB_ROLE", policyVersion);
        }
        if (!effective.implies(requested)) {
            return AccessDecision.deny("INSUFFICIENT_PERMISSION", policyVersion);
        }
        return AccessDecision.allow(policyVersion);
    }

    @Override
    public boolean canViewExcerpt(TenantId tenantId, UserId userId, long documentId) {
        return decideDocument(SubjectContext.user(tenantId, userId), documentId, DocumentPermission.VIEW_EXCERPT).allow();
    }

    @Override
    public boolean canViewContent(TenantId tenantId, UserId userId, long documentId) {
        return decideDocument(SubjectContext.user(tenantId, userId), documentId, DocumentPermission.VIEW_CONTENT).allow();
    }

    @Override
    public boolean canDownloadOriginal(TenantId tenantId, UserId userId, long documentId) {
        return decideDocument(SubjectContext.user(tenantId, userId), documentId,
                DocumentPermission.DOWNLOAD_ORIGINAL).allow();
    }

    // ---------- 内部工具 ----------

    /** KB 角色 → 默认文档能力（OWNER/EDITOR 可下载原件；VIEWER 可看正文；非成员默认拒绝）。 */
    private DocumentPermission basePermission(String kbRole) {
        if (permissionCatalog.kbRolePermissionIncludesDownload(kbRole)) {
            return DocumentPermission.DOWNLOAD_ORIGINAL;
        }
        if (PermissionCatalog.KB_ROLE_VIEWER.equals(kbRole)) {
            return DocumentPermission.VIEW_CONTENT;
        }
        return null;
    }

    private DocumentPermission maxAclPermission(java.util.List<DocumentAccessPort.AclEntry> acls,
                                                SubjectContext subject, String kbRole) {
        DocumentPermission max = null;
        for (DocumentAccessPort.AclEntry acl : acls) {
            if (!matchesPrincipal(acl, subject, kbRole)) {
                continue;
            }
            DocumentPermission permission = DocumentPermission.from(acl.permission());
            if (permission != null && (max == null || permission.rank() > max.rank())) {
                max = permission;
            }
        }
        return max;
    }

    private boolean matchesPrincipal(DocumentAccessPort.AclEntry acl, SubjectContext subject, String kbRole) {
        return switch (acl.principalType()) {
            case "USER" -> subject.userId() != null
                    && String.valueOf(subject.userId()).equals(acl.principalKey());
            case "ORG" -> subject.orgIds() != null
                    && subject.orgIds().stream().anyMatch(id -> String.valueOf(id).equals(acl.principalKey()));
            case "TENANT_ROLE" -> subject.tenantRoles() != null
                    && subject.tenantRoles().contains(acl.principalKey());
            case "KB_ROLE" -> kbRole != null && kbRole.equals(acl.principalKey());
            default -> false; // 未知 principal 类型默认不匹配（默认拒绝）
        };
    }
}
