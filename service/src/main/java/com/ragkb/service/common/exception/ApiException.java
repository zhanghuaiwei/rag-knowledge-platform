package com.ragkb.service.common.exception;

import com.ragkb.service.common.api.ApiResponse;

/**
 * 业务异常：由 Controller/Service 抛出，GlobalExceptionHandler 统一转换为
 * {@link ApiResponse}。业务异常与系统异常分开处理（全局 CLAUDE.md 错误处理约定）。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
