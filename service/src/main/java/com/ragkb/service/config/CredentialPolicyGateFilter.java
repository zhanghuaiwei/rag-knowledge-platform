package com.ragkb.service.config;

import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import com.ragkb.service.modules.identity.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

/**
 * 本地凭据策略门禁（仅 form + db 模式挂载，在 {@link JwtAuthenticationFilter} 之后）。
 *
 * <p>对已认证（JWT）请求按用户 id 重读 {@code user_credential}：若 {@code mustChangePassword}
 * 或密码已过期，则除白名单路径外一律 403（E-1008 / E-1007），引导到改密页。由于每次请求都
 * 从 DB 重读凭据，构造/绕过 token 无法跳过门禁。
 *
 * <p>白名单：{@code /api/v1/auth/session}（读取会话标志）、{@code /api/v1/auth/change-password}
 * （执行改密）、{@code /api/v1/auth/logout}、{@code /api/v1/ping}、{@code /actuator/**}。
 * API Key 主体（{@code ApiKeyPrincipal}）与匿名请求直接放行。
 *
 * <p>⚠️ 谨慎区（人工复核）：过期判定语义（与 {@code LocalAuthProperties.passwordExpiryDays > 0}
 * 的关系）、门禁与 {@code AuthServiceImpl} 会话 stamp 的一致性、以及每请求一次 DB 读的性能
 * 取舍，需人工确认。
 */
@Component
@Conditional(IdentityConditions.DbFormMode.class)
public class CredentialPolicyGateFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CredentialPolicyGateFilter.class);

    private final ObjectProvider<UserCredentialStorePort> credentialStoreProvider;

    public CredentialPolicyGateFilter(ObjectProvider<UserCredentialStorePort> credentialStoreProvider) {
        this.credentialStoreProvider = credentialStoreProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/change-password")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/ping")
                || path.startsWith("/api/v1/auth/session")
                || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal)) {
            // 匿名（认证兜底 401）或 API Key 主体不适用本地账号凭据策略
            chain.doFilter(request, response);
            return;
        }
        UserCredentialStorePort store = credentialStoreProvider.getIfAvailable();
        if (store == null) {
            chain.doFilter(request, response);
            return;
        }
        UserCredentialStorePort.CredentialRecord credential;
        try {
            credential = store.findByUserId(principal.userId()).orElse(null);
        } catch (RuntimeException e) {
            // 基础设施故障（如 DB 连接失败/超时）：fail-closed 503（E-9998），
            // 不裸抛为 Spring 默认 500 错误体（绕过 GlobalExceptionHandler，前端无法按信封处理）。
            log.error("凭据策略门禁 DB 读取异常: {}", e.getMessage(), e);
            sendServiceUnavailable(response);
            return;
        }
        if (credential == null) {
            // 无本地凭据（如 OIDC 绑定用户）不适用本地账号策略
            chain.doFilter(request, response);
            return;
        }
        if (credential.mustChangePassword()) {
            sendForbidden(response, ErrorCode.MUST_CHANGE_PASSWORD);
            return;
        }
        if (credential.passwordExpiresAt() != null && credential.passwordExpiresAt().isBefore(Instant.now())) {
            sendForbidden(response, ErrorCode.PASSWORD_EXPIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private void sendForbidden(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code.getCode()
                + "\",\"message\":\"" + code.getMessage() + "\",\"data\":null}");
    }

    private void sendServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(503);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"E-9998\",\"message\":\"服务暂不可用，请稍后重试\",\"data\":null}");
    }
}
