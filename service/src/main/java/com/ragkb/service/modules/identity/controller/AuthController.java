package com.ragkb.service.modules.identity.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.modules.identity.dto.LoginDto;
import com.ragkb.service.modules.identity.dto.SwitchTenantDto;
import com.ragkb.service.modules.identity.service.AuthService;
import com.ragkb.service.modules.identity.service.AuthService.AuthResult;
import com.ragkb.service.modules.identity.vo.AuthSessionVo;
import com.ragkb.service.modules.identity.vo.TokenResponseVo;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

/**
 * 认证域接口入口（OIDC/BFF 登录、账号密码登录 + JWT、刷新、会话、租户切换）。
 *
 * <p>登录方式由 {@code ragkb.auth.mode} 环境变量开关控制：
 * <ul>
 *   <li>{@code form}（开发默认）：{@code POST /api/v1/auth/login} 账号密码登录，签发 access token
 *       （响应体）+ refresh token（HttpOnly cookie {@code ragkb_refresh}）；刷新走
 *       {@code POST /api/v1/auth/refresh}（轮换 + 复用检测）。</li>
 *   <li>{@code oidc}（生产）：{@code GET /api/v1/auth/authorize} 重定向企业 IdP，BFF 会话 cookie。</li>
 * </ul>
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final boolean refreshCookieSecure;

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            @Value("${ragkb.cookie.secure:false}") boolean refreshCookieSecure) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.refreshCookieSecure = refreshCookieSecure;
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

    /** 账号密码登录（仅 form 模式开放）：签发 access token（响应体）+ refresh token（HttpOnly cookie）。 */
    @PostMapping("/api/v1/auth/login")
    public ApiResponse<TokenResponseVo> login(
            @Valid @RequestBody LoginDto request,
            HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(), request.password()));
        AuthResult result = authService.login(authentication);
        writeRefreshCookie(servletResponse, result.refreshToken(), result.refreshCookieMaxAge());
        return ApiResponse.ok(result.response());
    }

    /** 刷新：读取 HttpOnly refresh cookie，轮换并签发新 access token；复用检测失败 401。 */
    @PostMapping("/api/v1/auth/refresh")
    public ApiResponse<TokenResponseVo> refresh(
            @CookieValue(name = "ragkb_refresh", required = false) String refreshToken,
            HttpServletResponse servletResponse) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "缺少刷新凭证");
        }
        AuthResult result = authService.refresh(refreshToken);
        writeRefreshCookie(servletResponse, result.refreshToken(), result.refreshCookieMaxAge());
        return ApiResponse.ok(result.response());
    }

    /** 登出（幂等）：黑名单 access jti + 吊销 refresh 家族 + 清除 refresh cookie。 */
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @CookieValue(name = "ragkb_refresh", required = false) String refreshToken,
            HttpServletResponse servletResponse) {
        String accessToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        authService.logout(accessToken, refreshToken);
        clearRefreshCookie(servletResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/auth/session")
    public ApiResponse<AuthSessionVo> session() {
        return ApiResponse.ok(authService.session());
    }

    /**
     * 切换当前激活租户（JWT 模式）：服务端校验成员关系后重签 access + refresh，
     * 返回新 TokenResponse（含新租户上下文），refresh 重新写 HttpOnly cookie。
     */
    @PostMapping("/api/v1/auth/tenant/switch")
    public ApiResponse<TokenResponseVo> switchTenant(
            @Valid @RequestBody SwitchTenantDto request,
            HttpServletResponse servletResponse) {
        AuthResult result = authService.switchTenant(request.tenantId());
        writeRefreshCookie(servletResponse, result.refreshToken(), result.refreshCookieMaxAge());
        return ApiResponse.ok(result.response());
    }

    // ---------- refresh cookie 写入/清除 ----------

    private void writeRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from("ragkb_refresh", value)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        writeRefreshCookie(response, "", Duration.ZERO);
    }
}
