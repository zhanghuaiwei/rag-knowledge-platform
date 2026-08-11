package com.ragkb.service.interfaces;

import com.ragkb.service.common.ApiException;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一转换为 {@link ApiResponse}，业务异常与系统异常分开处理。
 * 后续接入请求上下文（requestId/tenantId/userId）后从 MDC 补充结构化日志字段。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": "
                        + (fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage() : "invalid"))
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    /**
     * 脚手架阶段：接口入口已就绪，业务实现点待人工实现。
     * 桩实现抛出 {@link UnsupportedOperationException}，统一映射为 501 Not Implemented。
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotImplemented(UnsupportedOperationException ex) {
        log.warn("not implemented entrypoint: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error("E-9998", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
