package com.ragkb.service.modules.access.domain;

/**
 * 文档级权限档位（对齐 {@code document_acl.permission} 枚举）。
 *
 * <p>蕴含关系由策略层统一展开：{@code DOWNLOAD_ORIGINAL -> VIEW_CONTENT -> VIEW_EXCERPT}，
 * 调用方不得各自推断（认证授权 §5.2）。
 */
public enum DocumentPermission {

    VIEW_EXCERPT(1),
    VIEW_CONTENT(2),
    DOWNLOAD_ORIGINAL(3);

    private final int rank;

    DocumentPermission(int rank) {
        this.rank = rank;
    }

    /** 权限档位（策略层取"更高档位"提升 ACL 时使用）。 */
    public int rank() {
        return rank;
    }

    /** 当前权限是否能覆盖所请求权限（rank 蕴含）。 */
    public boolean implies(DocumentPermission requested) {
        return this.rank >= requested.rank;
    }

    /** 从 {@code document_acl.permission} 字符串解析；未知返回 null（默认拒绝）。 */
    public static DocumentPermission from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return DocumentPermission.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
