package com.ragkb.service.modules.access.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogTest {

    private final PermissionCatalog catalog = new PermissionCatalog();

    @Test
    void tenantAdminGetsFullPermissions() {
        Set<String> permissions = catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_TENANT_ADMIN));
        assertTrue(permissions.contains(PermissionCatalog.API_KEY_MANAGE));
        assertTrue(permissions.contains(PermissionCatalog.TENANT_MEMBER_MANAGE));
        assertTrue(permissions.contains(PermissionCatalog.AUDIT_READ));
        assertTrue(permissions.contains(PermissionCatalog.REVIEW_DECIDE));
        assertTrue(permissions.contains(PermissionCatalog.RETENTION_MANAGE));
        assertTrue(permissions.contains(PermissionCatalog.DASHBOARD_VIEW));
    }

    @Test
    void memberHasBaseConsumptionOnly() {
        Set<String> permissions = catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_MEMBER));
        assertTrue(permissions.contains(PermissionCatalog.KB_LIST));
        assertTrue(permissions.contains(PermissionCatalog.CHAT_USE));
        assertFalse(permissions.contains(PermissionCatalog.API_KEY_MANAGE));
        assertFalse(permissions.contains(PermissionCatalog.AUDIT_READ));
        assertFalse(permissions.contains(PermissionCatalog.REVIEW_LIST));
    }

    @Test
    void securityAdminManagesKeysAndAuditNotContentGovernance() {
        Set<String> permissions = catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_SECURITY_ADMIN));
        assertTrue(permissions.contains(PermissionCatalog.API_KEY_MANAGE));
        assertTrue(permissions.contains(PermissionCatalog.AUDIT_READ));
        assertFalse(permissions.contains(PermissionCatalog.METADATA_SCHEMA_MANAGE));
        assertFalse(permissions.contains(PermissionCatalog.TENANT_MEMBER_MANAGE));
    }

    @Test
    void auditorReadsAuditAndDeletionProof() {
        Set<String> permissions = catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_AUDITOR));
        assertTrue(permissions.contains(PermissionCatalog.AUDIT_READ));
        assertTrue(permissions.contains(PermissionCatalog.DELETION_READ));
        assertFalse(permissions.contains(PermissionCatalog.REVIEW_DECIDE));
        assertFalse(permissions.contains(PermissionCatalog.METADATA_SCHEMA_MANAGE));
    }

    @Test
    void unknownRoleContributesNothing() {
        Set<String> permissions = catalog.permissionsForRoles(List.of("SUPER_USER"));
        assertEquals(Set.of(), permissions);
        assertEquals(List.of(), catalog.permissionListForRoles(List.of("SUPER_USER")));
    }

    @Test
    void multiRoleUnion() {
        Set<String> permissions = catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_AUDITOR));
        assertTrue(permissions.contains(PermissionCatalog.ANALYTICS_READ));
    }

    @Test
    void featuresDerivedFromPermissions() {
        assertTrue(catalog.featuresFor(
                catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_TENANT_ADMIN))).contains("governance"));
        assertTrue(catalog.featuresFor(
                catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_TENANT_ADMIN))).contains("analytics"));
        assertFalse(catalog.featuresFor(
                catalog.permissionsForRoles(List.of(PermissionCatalog.ROLE_MEMBER))).contains("governance"));
    }

    @Test
    void kbRoleDownloadCapability() {
        assertTrue(catalog.kbRolePermissionIncludesDownload(PermissionCatalog.KB_ROLE_OWNER));
        assertTrue(catalog.kbRolePermissionIncludesDownload(PermissionCatalog.KB_ROLE_EDITOR));
        assertFalse(catalog.kbRolePermissionIncludesDownload(PermissionCatalog.KB_ROLE_VIEWER));
        assertFalse(catalog.kbRolePermissionIncludesDownload("UNKNOWN"));
    }
}
