package com.ragkb.service.common.api;

import java.util.List;

/**
 * 游标分页响应（对齐 OpenAPI CursorPageData）。
 *
 * @param <T> 列表元素类型
 */
public record CursorPageData<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPageData<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new CursorPageData<>(items, nextCursor, hasMore);
    }

    public static <T> CursorPageData<T> empty() {
        return of(List.of(), null, false);
    }
}
