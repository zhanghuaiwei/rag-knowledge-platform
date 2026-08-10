package com.ragkb.service.common;

import java.util.List;

/**
 * 分页响应：items / total / page / size / hasMore（07-API契约 §1）。
 */
public record PageResult<T>(List<T> items, long total, int page, int size, boolean hasMore) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        boolean hasMore = (long) page * size < total;
        return new PageResult<>(items, total, page, size, hasMore);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), 0, page, size);
    }
}
