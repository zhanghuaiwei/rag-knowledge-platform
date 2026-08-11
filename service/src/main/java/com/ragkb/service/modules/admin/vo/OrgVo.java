package com.ragkb.service.modules.admin.vo;

/**
 * 组织节点响应视图（对齐前端 Admin 契约）。
 */
public record OrgVo(
        long id,
        Long parentId,
        String name,
        String path,
        long memberCount,
        String status) {
}
