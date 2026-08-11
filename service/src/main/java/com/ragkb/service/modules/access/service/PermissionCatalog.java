package com.ragkb.service.modules.access.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限目录：集中维护"角色是权限集合"（认证授权设计 §5.3、动态菜单 §5.3）。
 *
 * <p>约定：
 * <ul>
 *   <li>角色→权限映射只在服务端维护，前端/菜单/Controller 不各自推断；</li>
 *   <li>{@link #permissionsForRoles} 用于方法级授权（{@code @PreAuthorize}）与动态菜单的权限视图；</li>
 *   <li>未知角色/权限一律不返回（默认拒绝，不宽松回退）；</li>
 *   <li>features 由权限推导（dev 模型），生产可改为租户套餐/部署能力配置后覆盖。</li>
 * </ul>
 */
@Component
public class PermissionCatalog {

    // ---------- 租户级权限码（与动态菜单 §5.3 建议对齐，最终以 OpenAPI 契约为准） ----------

    public static final String DASHBOARD_VIEW = "dashboard:view";
    public static final String CHAT_USE = "chat:use";
    public static final String SEARCH_EXECUTE = "search:execute";
    public static final String KB_LIST = "kb:list";
    public static final String KB_MANAGE = "kb:manage";
    public static final String DOCUMENT_LIST = "document:list";
    public static final String FAVORITE_LIST = "favorite:list";
    public static final String REVIEW_LIST = "review:list";
    public static final String REVIEW_DECIDE = "review:decide";
    public static final String METADATA_SCHEMA_MANAGE = "metadata-schema:manage";
    public static final String RETENTION_MANAGE = "retention:manage";
    public static final String DELETION_READ = "deletion:read";
    public static final String ANALYTICS_READ = "analytics:read";
    public static final String ANALYTICS_SCREEN = "analytics:screen";
    public static final String TENANT_MEMBER_MANAGE = "tenant-member:manage";
    public static final String TAG_MANAGE = "tag:manage";
    public static final String API_KEY_MANAGE = "api-key:manage";
    public static final String WEBHOOK_MANAGE = "webhook:manage";
    public static final String AUDIT_READ = "audit:read";

    // ---------- 租户角色（tenant_member_role 约束枚举） ----------

    public static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    public static final String ROLE_SECURITY_ADMIN = "SECURITY_ADMIN";
    public static final String ROLE_KNOWLEDGE_ADMIN = "KNOWLEDGE_ADMIN";
    public static final String ROLE_AUDITOR = "AUDITOR";
    public static final String ROLE_MEMBER = "MEMBER";

    // ---------- 知识库角色（kb_member 约束枚举） ----------

    public static final String KB_ROLE_OWNER = "OWNER";
    public static final String KB_ROLE_EDITOR = "EDITOR";
    public static final String KB_ROLE_VIEWER = "VIEWER";

    /** 全部角色共有的知识消费基础权限。 */
    private static final Set<String> BASE_CONSUMPTION = Set.of(
            DASHBOARD_VIEW, CHAT_USE, SEARCH_EXECUTE, KB_LIST, DOCUMENT_LIST, FAVORITE_LIST);

    /** 基础消费权限 + 角色专属权限 → 不可变集合。 */
    private static Set<String> withBase(Set<String> extra) {
        Set<String> all = new LinkedHashSet<>(BASE_CONSUMPTION);
        all.addAll(extra);
        return Set.copyOf(all);
    }

    private static final Map<String, Set<String>> TENANT_ROLE_PERMISSIONS = Map.of(
            ROLE_TENANT_ADMIN, withBase(Set.of(
                    KB_MANAGE, REVIEW_LIST, REVIEW_DECIDE, METADATA_SCHEMA_MANAGE,
                    RETENTION_MANAGE, DELETION_READ, ANALYTICS_READ, ANALYTICS_SCREEN,
                    TENANT_MEMBER_MANAGE, TAG_MANAGE, API_KEY_MANAGE, WEBHOOK_MANAGE, AUDIT_READ)),
            ROLE_SECURITY_ADMIN, withBase(Set.of(API_KEY_MANAGE, WEBHOOK_MANAGE, AUDIT_READ)),
            ROLE_KNOWLEDGE_ADMIN, withBase(Set.of(
                    KB_MANAGE, REVIEW_LIST, REVIEW_DECIDE, METADATA_SCHEMA_MANAGE,
                    RETENTION_MANAGE, DELETION_READ, TAG_MANAGE, ANALYTICS_READ)),
            ROLE_AUDITOR, withBase(Set.of(AUDIT_READ, DELETION_READ, ANALYTICS_READ)),
            ROLE_MEMBER, withBase(Set.of()));

    /** KB 角色 → 内容操作权限（资源级由 AccessPolicyUseCase 判定，此处仅能力集合）。 */
    private static final Map<String, Set<String>> KB_ROLE_PERMISSIONS = Map.of(
            KB_ROLE_VIEWER, Set.of("kb:view"),
            KB_ROLE_EDITOR, Set.of("kb:view", "kb:edit"),
            KB_ROLE_OWNER, Set.of("kb:view", "kb:edit", "kb:manage"));

    /** feature → 所需权限（dev 推导模型；生产可由租户配置覆盖）。 */
    private static final Map<String, Set<String>> FEATURE_REQUIRED_PERMISSIONS = Map.of(
            "governance", Set.of(REVIEW_LIST, METADATA_SCHEMA_MANAGE, RETENTION_MANAGE, DELETION_READ),
            "analytics", Set.of(ANALYTICS_READ, ANALYTICS_SCREEN));

    /** 租户角色列表 → 权限集合（未知角色不贡献权限，默认拒绝）。 */
    public Set<String> permissionsForRoles(List<String> tenantRoles) {
        Set<String> permissions = new LinkedHashSet<>();
        for (String role : tenantRoles) {
            Set<String> rolePermissions = TENANT_ROLE_PERMISSIONS.get(role);
            if (rolePermissions != null) {
                permissions.addAll(rolePermissions);
            }
        }
        return permissions;
    }

    /** 单个 KB 角色的内容能力集合；未知角色返回空（默认拒绝）。 */
    public Set<String> permissionsForKbRole(String kbRole) {
        Set<String> permissions = KB_ROLE_PERMISSIONS.get(kbRole);
        return permissions != null ? Set.copyOf(permissions) : Set.of();
    }

    /** 从权限集合推导租户已启用的 feature（dev 模型，生产可覆盖）。 */
    public Set<String> featuresFor(Set<String> permissions) {
        Set<String> features = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : FEATURE_REQUIRED_PERMISSIONS.entrySet()) {
            if (!entry.getValue().isEmpty() && entry.getValue().stream().anyMatch(permissions::contains)) {
                features.add(entry.getKey());
            }
        }
        return features;
    }

    /** 便捷：给定租户角色返回非空权限列表（会话视图、@PreAuthorize 展开共用）。 */
    public List<String> permissionListForRoles(List<String> tenantRoles) {
        return new ArrayList<>(permissionsForRoles(tenantRoles));
    }

    /** KB 角色是否具备下载原件能力（OWNER/EDITOR；VIEWER 默认可看正文不可下载）。 */
    public boolean kbRolePermissionIncludesDownload(String kbRole) {
        return KB_ROLE_OWNER.equals(kbRole) || KB_ROLE_EDITOR.equals(kbRole);
    }
}
