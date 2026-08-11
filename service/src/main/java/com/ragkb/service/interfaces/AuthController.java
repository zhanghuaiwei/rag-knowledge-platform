package com.ragkb.service.interfaces;

import com.ragkb.service.application.AuthService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.interfaces.dto.AuthDtos.AuthSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 认证域接口入口（OIDC/BFF 登录、账号密码登录、会话、租户切换）。
 *
 * <p>登录方式由 {@code ragkb.auth.mode} 环境变量开关控制：
 * <ul>
 *   <li>{@code form}（开发默认）：{@code POST /api/v1/auth/login} 账号密码登录。</li>
 *   <li>{@code oidc}（生产）：{@code GET /api/v1/auth/authorize} 重定向企业 IdP。</li>
 * </ul>
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /** 登录入口：form 模式重定向登录页；oidc 模式重定向 IdP。 */
    @GetMapping("/api/v1/auth/authorize")
    public ResponseEntity<Void> authorize(@RequestParam(required = false) String returnTo) {
        String url = authService.buildAuthorizeUrl(returnTo);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** OIDC callback：由 Spring Security oauth2Login 处理，此处回跳首页。 */
    @GetMapping("/api/v1/auth/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        authService.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
    }

    /** 账号密码登录（仅 form 模式开放）：认证成功建立会话 cookie，返回会话概览。 */
    @PostMapping("/api/v1/auth/login")
    public ApiResponse<AuthSession> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // 持久化到会话，后续请求携带 JSESSIONID 即已认证
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return ApiResponse.ok(authService.session());
    }

    @GetMapping("/api/v1/auth/session")
    public ApiResponse<AuthSession> session() {
        return ApiResponse.ok(authService.session());
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        authService.logout();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/auth/tenant/switch")
    public ApiResponse<AuthSession> switchTenant(@Valid @RequestBody SwitchTenantRequest request) {
        return ApiResponse.ok(authService.switchTenant(request.tenantId()));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record SwitchTenantRequest(@NotNull Long tenantId) {
    }
}
