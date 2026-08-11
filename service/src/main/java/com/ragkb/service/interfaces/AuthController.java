package com.ragkb.service.interfaces;

import com.ragkb.service.application.AuthService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.interfaces.dto.AuthDtos.AuthSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 认证域接口入口（OIDC/BFF 登录、会话、租户切换）。业务实现见 {@link AuthService}。
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 302 重定向到 IdP。 */
    @GetMapping("/api/v1/auth/authorize")
    public ResponseEntity<Void> authorize(@RequestParam(required = false) String returnTo) {
        String url = authService.buildAuthorizeUrl(returnTo);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** OIDC callback：设置 BFF 会话 cookie 后回跳。 */
    @GetMapping("/api/v1/auth/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        authService.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
    }

    @GetMapping("/api/v1/auth/session")
    public ApiResponse<AuthSession> session() {
        return ApiResponse.ok(authService.session());
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/auth/tenant/switch")
    public ApiResponse<AuthSession> switchTenant(@Valid @RequestBody SwitchTenantRequest request) {
        return ApiResponse.ok(authService.switchTenant(request.tenantId()));
    }

    public record SwitchTenantRequest(@NotNull Long tenantId) {
    }
}
