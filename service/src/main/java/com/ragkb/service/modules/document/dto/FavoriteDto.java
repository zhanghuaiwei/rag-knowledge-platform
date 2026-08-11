package com.ragkb.service.modules.document.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 收藏/取消收藏入参。
 */
public record FavoriteDto(@NotNull Long documentId) {
}
