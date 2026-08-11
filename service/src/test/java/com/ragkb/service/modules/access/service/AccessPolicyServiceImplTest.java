package com.ragkb.service.modules.access.service;

import com.ragkb.service.modules.access.domain.AccessDecision;
import com.ragkb.service.modules.access.domain.DocumentPermission;
import com.ragkb.service.modules.access.domain.SubjectContext;
import com.ragkb.service.modules.document.port.DocumentAccessPort;
import com.ragkb.service.modules.knowledge.port.KbAccessPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessPolicyServiceImplTest {

    @Mock
    private KbAccessPort kbAccessPort;

    @Mock
    private DocumentAccessPort documentAccessPort;

    private AccessPolicyServiceImpl policy;

    @BeforeEach
    void setUp() {
        policy = new AccessPolicyServiceImpl(kbAccessPort, documentAccessPort, new PermissionCatalog());
    }

    // ---------- 辅助 ----------

    private SubjectContext user(long tenantId, long userId, List<String> tenantRoles) {
        return new SubjectContext(SubjectContext.ActorType.USER, userId, null, userId,
                tenantId, tenantRoles, List.of(), List.of(), 1L, null);
    }

    private void stubDocument(long tenantId, long documentId, long kbId, String status,
                              Boolean disabled, Integer delFlag, Long policyVersion,
                              List<DocumentAccessPort.AclEntry> acls) {
        when(documentAccessPort.viewOf(tenantId, documentId)).thenReturn(Optional.of(
                new DocumentAccessPort.DocumentAccessView(documentId, kbId, status, disabled,
                        delFlag, policyVersion, acls)));
    }

    private DocumentAccessPort.AclEntry acl(String type, String key, String permission) {
        return new DocumentAccessPort.AclEntry(type, key, permission);
    }

    // ---------- 状态门禁 ----------

    @Test
    void missingDocumentDenied() {
        when(documentAccessPort.viewOf(1L, 999L)).thenReturn(Optional.empty());
        AccessDecision decision = policy.decideDocument(
                user(1L, 42L, List.of("MEMBER")), 999L, DocumentPermission.VIEW_EXCERPT);
        assertFalse(decision.allow());
        assertTrue(decision.reasonCode().contains("NOT_FOUND"));
    }

    @Test
    void deletedAndDisabledDenied() {
        SubjectContext subject = user(1L, 42L, List.of("TENANT_ADMIN"));
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, 1, 5L, List.of());
        AccessDecision deleted = policy.decideDocument(subject, 1L, DocumentPermission.VIEW_EXCERPT);
        assertFalse(deleted.allow());
        assertTrue(deleted.reasonCode().contains("DELETED"));

        stubDocument(1L, 2L, 1L, "PUBLISHED", true, null, 5L, List.of());
        AccessDecision disabled = policy.decideDocument(subject, 2L, DocumentPermission.VIEW_CONTENT);
        assertFalse(disabled.allow());
        assertTrue(disabled.reasonCode().contains("DISABLED"));

        stubDocument(1L, 3L, 1L, "DRAFT", false, null, 5L, List.of());
        AccessDecision draft = policy.decideDocument(subject, 3L, DocumentPermission.VIEW_CONTENT);
        assertFalse(draft.allow());
        assertTrue(draft.reasonCode().contains("DRAFT"));
    }

    // ---------- KB 角色能力 ----------

    @Test
    void viewerCanViewContentButNotDownload() {
        SubjectContext subject = user(1L, 42L, List.of("MEMBER"));
        when(kbAccessPort.roleOf(1L, 42L, 1L)).thenReturn(Optional.of("VIEWER"));
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, null, 5L, List.of());
        assertTrue(policy.decideDocument(subject, 1L, DocumentPermission.VIEW_CONTENT).allow());
        assertFalse(policy.decideDocument(subject, 1L, DocumentPermission.DOWNLOAD_ORIGINAL).allow());
    }

    @Test
    void ownerCanDownload() {
        SubjectContext subject = user(1L, 42L, List.of("KNOWLEDGE_ADMIN"));
        when(kbAccessPort.roleOf(1L, 42L, 1L)).thenReturn(Optional.of("OWNER"));
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, null, 5L, List.of());
        assertTrue(policy.decideDocument(subject, 1L, DocumentPermission.DOWNLOAD_ORIGINAL).allow());
    }

    @Test
    void noKbRoleDeniedByDefault() {
        SubjectContext subject = user(1L, 42L, List.of("MEMBER"));
        when(kbAccessPort.roleOf(1L, 42L, 1L)).thenReturn(Optional.empty());
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, null, 5L, List.of());
        AccessDecision decision = policy.decideDocument(subject, 1L, DocumentPermission.VIEW_EXCERPT);
        assertFalse(decision.allow());
        assertTrue(decision.reasonCode().contains("NO_KB_ROLE"));
    }

    // ---------- ACL 提升 ----------

    @Test
    void aclUserGrantRaisesPermissionWithoutKbRole() {
        SubjectContext subject = user(1L, 42L, List.of("MEMBER"));
        when(kbAccessPort.roleOf(1L, 42L, 1L)).thenReturn(Optional.empty());
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, null, 5L,
                List.of(acl("USER", "42", "DOWNLOAD_ORIGINAL")));
        assertTrue(policy.decideDocument(subject, 1L, DocumentPermission.DOWNLOAD_ORIGINAL).allow());
    }

    @Test
    void aclTenantRoleGrantMatchesSubjectRole() {
        SubjectContext subject = user(1L, 42L, List.of("AUDITOR"));
        when(kbAccessPort.roleOf(1L, 42L, 1L)).thenReturn(Optional.of("VIEWER"));
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, null, 5L,
                List.of(acl("TENANT_ROLE", "AUDITOR", "VIEW_CONTENT")));
        // VIEWER 基础已是 VIEW_CONTENT，ACL 不额外降低；下载仍拒绝
        assertTrue(policy.decideDocument(subject, 1L, DocumentPermission.VIEW_CONTENT).allow());
        assertFalse(policy.decideDocument(subject, 1L, DocumentPermission.DOWNLOAD_ORIGINAL).allow());
    }

    @Test
    void unknownAclPrincipalTypeIgnored() {
        SubjectContext subject = user(1L, 42L, List.of("MEMBER"));
        when(kbAccessPort.roleOf(1L, 42L, 1L)).thenReturn(Optional.empty());
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, null, 5L,
                List.of(acl("HACKER", "x", "DOWNLOAD_ORIGINAL")));
        assertFalse(policy.decideDocument(subject, 1L, DocumentPermission.VIEW_EXCERPT).allow());
    }

    // ---------- 便捷布尔方法 ----------

    @Test
    void convenienceMethodsDelegateToDecision() {
        when(kbAccessPort.roleOf(1L, 42L, 1L)).thenReturn(Optional.of("OWNER"));
        stubDocument(1L, 1L, 1L, "PUBLISHED", false, null, 5L, List.of());
        assertTrue(policy.canViewExcerpt(new com.ragkb.service.common.model.TenantId(1L),
                new com.ragkb.service.common.model.UserId(42L), 1L));
        assertTrue(policy.canViewContent(new com.ragkb.service.common.model.TenantId(1L),
                new com.ragkb.service.common.model.UserId(42L), 1L));
        assertTrue(policy.canDownloadOriginal(new com.ragkb.service.common.model.TenantId(1L),
                new com.ragkb.service.common.model.UserId(42L), 1L));
    }
}
