package com.ragkb.service.config;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.modules.access.service.PermissionCatalog;
import com.ragkb.service.modules.identity.port.TokenBlacklistPort;
import com.ragkb.service.modules.identity.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器（仅 form 模式挂载）：读取 {@code Authorization: Bearer <accessToken>}，
 * 校验签名/过期并检查黑名单，通过后写 SecurityContext；无 token 直接放行
 * （后续由 {@code authorizeHttpRequests} 兜底 401）。
 *
 * <p>⚠️ 在 {@link TokenService} 人工实现完成前，桩抛出的 {@code UnsupportedOperationException}
 * 会以 500 返回（web 端默认走 mock，不受影响）；实现完成后签名/过期失败返回 401。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenService tokenService;
    private final TokenBlacklistPort blacklistPort;
    private final PermissionCatalog permissionCatalog;

    public JwtAuthenticationFilter(TokenService tokenService, TokenBlacklistPort blacklistPort,
                                   PermissionCatalog permissionCatalog) {
        this.tokenService = tokenService;
        this.blacklistPort = blacklistPort;
        this.permissionCatalog = permissionCatalog;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            TokenService.JwtPrincipal principal = tokenService.parseAccess(token);
            if (blacklistPort.isBlacklisted(principal.jti())) {
                sendUnauthorized(response);
                return;
            }
            // 方法级授权基于稳定权限码（角色→权限集中在 PermissionCatalog 展开），
            // 而非原始 credential scope，支撑 @PreAuthorize("hasAuthority('api-key:manage')")。
            var authorities = permissionCatalog.permissionsForRoles(principal.tenantRoles()).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(principal, token, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ApiException e) {
            sendUnauthorized(response);
            return;
        } catch (RuntimeException e) {
            // 基础设施故障（如 Redis 黑名单查询超时/连接失败）：fail-closed 503（E-9998），
            // 不裸抛为 Spring 默认 500 错误体（绕过 GlobalExceptionHandler，前端无法按信封处理）。
            log.error("JWT 认证链基础设施异常（Redis 黑名单查询等）: {}", e.getMessage(), e);
            sendServiceUnavailable(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"E-1001\",\"message\":\"未认证或登录已过期\",\"data\":null}");
    }

    private void sendServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(503);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"E-9998\",\"message\":\"认证服务暂不可用，请稍后重试\",\"data\":null}");
    }
}
