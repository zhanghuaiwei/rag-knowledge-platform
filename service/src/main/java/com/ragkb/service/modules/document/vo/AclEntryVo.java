package com.ragkb.service.modules.document.vo;

import java.util.List;

/**
 * 文档级权限条目响应视图（F2.14 / GKB-04）。
 */
public record AclEntryVo(long id, String principalType, String principalName, List<String> permissions) {
}
