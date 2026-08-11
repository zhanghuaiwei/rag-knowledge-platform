package com.ragkb.service.modules.identity.controller;

import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.identity.vo.ApiKeyVo;
import com.ragkb.service.modules.identity.vo.ApiKeyCreatedVo;
import com.ragkb.service.modules.identity.dto.ApiKeyCreateDto;
import com.ragkb.service.modules.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * scoped API Key 接口入口（机器访问）。业务实现见 {@link AuthService}。
 */
@RestController
public class ApiKeyController {

    private final AuthService authService;

    public ApiKeyController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/v1/api-keys")
    public ApiResponse<List<ApiKeyVo>> listApiKeys() {
        return ApiResponse.ok(authService.listApiKeys());
    }

    @PostMapping("/api/v1/api-keys")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApiKeyCreatedVo> createApiKey(
            @Valid @RequestBody ApiKeyCreateDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(authService.createApiKey(request, idempotencyKey));
    }

    @GetMapping("/api/v1/api-keys/{keyId}")
    public ApiResponse<ApiKeyVo> getApiKey(@PathVariable long keyId) {
        // 复用列表实现入口；明细查询留待人工实现（AuthService 可补充 getApiKey）。
        return ApiResponse.ok(authService.listApiKeys().stream()
                .filter(key -> key.id() == keyId)
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.exception.ApiException(
                        com.ragkb.service.common.exception.ErrorCode.NOT_FOUND, "API Key 不存在")));
    }

    @DeleteMapping("/api/v1/api-keys/{keyId}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable long keyId) {
        authService.revokeApiKey(keyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/api-keys/{keyId}/rotate")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApiKeyCreatedVo> rotateApiKey(
            @PathVariable long keyId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(authService.rotateApiKey(keyId, idempotencyKey));
    }
}
