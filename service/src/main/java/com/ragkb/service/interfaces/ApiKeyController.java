package com.ragkb.service.interfaces;

import com.ragkb.service.application.AuthService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKey;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreated;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreateRequest;
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
    public ApiResponse<List<ApiKey>> listApiKeys() {
        return ApiResponse.ok(authService.listApiKeys());
    }

    @PostMapping("/api/v1/api-keys")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApiKeyCreated> createApiKey(
            @Valid @RequestBody ApiKeyCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(authService.createApiKey(request, idempotencyKey));
    }

    @GetMapping("/api/v1/api-keys/{keyId}")
    public ApiResponse<ApiKey> getApiKey(@PathVariable long keyId) {
        // 复用列表实现入口；明细查询留待人工实现（AuthService 可补充 getApiKey）。
        return ApiResponse.ok(authService.listApiKeys().stream()
                .filter(key -> key.id() == keyId)
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.ApiException(
                        com.ragkb.service.common.ErrorCode.NOT_FOUND, "API Key 不存在")));
    }

    @DeleteMapping("/api/v1/api-keys/{keyId}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable long keyId) {
        authService.revokeApiKey(keyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/api-keys/{keyId}/rotate")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApiKeyCreated> rotateApiKey(
            @PathVariable long keyId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(authService.rotateApiKey(keyId, idempotencyKey));
    }
}
