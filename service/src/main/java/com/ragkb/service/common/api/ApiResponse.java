package com.ragkb.service.common.api;

/**
 * 统一响应信封：{@code { code, message, data }}（03-详细设计 §9）。
 *
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "0";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "OK", data);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
