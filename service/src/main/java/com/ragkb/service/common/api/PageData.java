package com.ragkb.service.common.api;

import java.util.List;

/**
 * 页式分页响应（对齐前端 PageResult 与 OpenAPI PageData）。
 *
 * @param <T> 列表元素类型
 */
public record PageData<T>(List<T> items, long total, int page, int size, boolean hasMore) {

    public static <T> PageData<T> of(List<T> items, long total, int page, int size) {
        return new PageData<>(items, total, page, size, (long) page * size < total);
    }

    public static <T> PageData<T> empty(int page, int size) {
        return of(List.of(), 0, page, size);
    }
}
