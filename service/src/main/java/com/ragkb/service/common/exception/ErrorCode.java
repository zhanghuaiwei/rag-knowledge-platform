package com.ragkb.service.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码（权威字典见 03-详细设计 §9）。code 与 HTTP 状态一一映射，
 * 前端按 code 做差异化处理（07-API契约 §7）。
 */
public enum ErrorCode {

    SUCCESS("0", "OK", HttpStatus.OK),
    BAD_REQUEST("E-1000", "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("E-1001", "未认证或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("E-1002", "无权限执行该操作", HttpStatus.FORBIDDEN),
    NOT_FOUND("E-1003", "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT("E-1004", "资源状态冲突", HttpStatus.CONFLICT),
    RATE_LIMITED("E-1005", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("E-9999", "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
