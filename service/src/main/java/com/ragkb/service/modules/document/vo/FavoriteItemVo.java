package com.ragkb.service.modules.document.vo;

/**
 * 收藏条目响应视图（对齐前端 Document 收藏契约）。
 */
public record FavoriteItemVo(long documentId, String title, String fileName, String kbName, String savedAt) {
}
