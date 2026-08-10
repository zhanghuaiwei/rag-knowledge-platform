package com.ragkb.service.common;

/**
 * 分页请求参数：page 从 1 起，size 默认 20、上限 100（07-API契约 §1）。
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > MAX_SIZE) {
            size = DEFAULT_SIZE;
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }
}
