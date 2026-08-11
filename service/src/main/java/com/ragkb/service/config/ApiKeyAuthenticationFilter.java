package com.ragkb.service.config;

import com.ragkb.service.modules.identity.adapter.ApiKeyCrypto;
import com.ragkb.service.modules.identity.port.ApiKeyStorePort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key 认证过滤器（仅 db.enabled=true 时挂载，且必须在 JWT 过滤器之前）。
 *
 * <p>Bearer 载荷以 {@code rk_} 前缀区分 API Key 与 JWT（认证授权 §4.3：认证层解析 key 类型，
 * 不能把 API Key 误交给 JWT Parser）。校验 prefix+digest 命中 ACTIVE 且未过期记录，
 * 构造 {@link ApiKeyPrincipal} 写入 SecurityContext；authorities 来自 key 的 scope。
 *
 * <p>{@code last_used_at} 限频更新（≥60s 一次），避免每请求写热点行。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** API Key 认证主体（下游 Service 可识别 actorType=API_KEY）。 */
    public record ApiKeyPrincipal(long keyId, long tenantId, List<String> scopes, List<Long> allowedKbIds) {
    }

    private static final Duration LAST_USED_THROTTLE = Duration.ofSeconds(60);

    private final ApiKeyStorePort apiKeyStore;
    private final ApiKeyCrypto apiKeyCrypto;
    private final Map<Long, Instant> lastTouchedAt = new ConcurrentHashMap<>();

    public ApiKeyAuthenticationFilter(ApiKeyStorePort apiKeyStore, ApiKeyCrypto apiKeyCrypto) {
        this.apiKeyStore = apiKeyStore;
        this.apiKeyCrypto = apiKeyCrypto;
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
        if (!apiKeyCrypto.looksLikeApiKey(token)) {
            // 非 API Key：交给 JWT 过滤器
            chain.doFilter(request, response);
            return;
        }

        Optional<ApiKeyStorePort.ApiKeyRecord> record = apiKeyStore.findActiveByPrefixAndDigest(
                apiKeyCrypto.prefix(token), apiKeyCrypto.digest(token));
        if (record.isEmpty()) {
            sendUnauthorized(response);
            return;
        }
        ApiKeyStorePort.ApiKeyRecord key = record.get();
        maybeTouchLastUsed(key.id(), key.tenantId());

        var authorities = key.scopes().stream().map(SimpleGrantedAuthority::new).toList();
        var principal = new ApiKeyPrincipal(key.id(), key.tenantId(), key.scopes(), key.allowedKbIds());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, token, authorities));
        chain.doFilter(request, response);
    }

    private void maybeTouchLastUsed(long keyId, long tenantId) {
        Instant now = Instant.now();
        Instant last = lastTouchedAt.get(keyId);
        if (last != null && Duration.between(last, now).compareTo(LAST_USED_THROTTLE) < 0) {
            return;
        }
        lastTouchedAt.put(keyId, now);
        apiKeyStore.touchLastUsedAt(tenantId, keyId, now);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"E-1001\",\"message\":\"API Key 无效或已过期\",\"data\":null}");
    }
}
