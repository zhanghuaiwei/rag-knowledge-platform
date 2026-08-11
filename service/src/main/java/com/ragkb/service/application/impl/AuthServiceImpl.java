package com.ragkb.service.application.impl;

import com.ragkb.service.application.AuthService;
import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.common.ApiException;
import com.ragkb.service.common.ErrorCode;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKey;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreated;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreateRequest;
import com.ragkb.service.interfaces.dto.AuthDtos.AuthSession;
import com.ragkb.service.interfaces.dto.AuthDtos.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证会话用例。
 *
 * <p>基础设施部分（会话概览/登出钩子/授权地址）基于 Spring Security 实现；
 * 真实用户体系、租户映射与 API Key 落库由人工实现（见各方法 TODO）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final String authMode;

    public AuthServiceImpl(@Value("${ragkb.auth.mode:form}") String authMode) {
        this.authMode = authMode;
    }

    @Override
    public String buildAuthorizeUrl(String returnTo) {
        if ("oidc".equals(authMode)) {
            // 触发 Spring Security OAuth2 授权流程（重定向 IdP）
            return "/oauth2/authorization/oidc";
        }
        return "/api/v1/auth/login";
    }

    @Override
    public void handleCallback(String code, String state) {
        // OIDC 回调由 Spring Security oauth2Login 完成，此处仅作端点占位；
        // 需要会话补充逻辑（如首次登录建号）时由人工实现。
    }

    @Override
    public AuthSession session() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
        }
        return toAuthSession(authentication);
    }

    @Override
    public void logout() {
        // SecurityContext/会话清理由 AuthController 的 SecurityContextLogoutHandler 完成；
        // 业务清理（如吊销刷新令牌 / 记录登出审计）为人工实现点。
    }

    @Override
    public AuthSession switchTenant(long tenantId) {
        return NotYetImplemented.stub("AuthService#switchTenant（人工实现：按 03-详细设计 §4 校验租户成员后切换激活租户）");
    }

    // ---------- API Key（人工实现落库与签名校验） ----------

    @Override
    public List<ApiKey> listApiKeys() {
        return NotYetImplemented.stub("AuthService#listApiKeys");
    }

    @Override
    public ApiKeyCreated createApiKey(ApiKeyCreateRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("AuthService#createApiKey");
    }

    @Override
    public void revokeApiKey(long keyId) {
        NotYetImplemented.stub("AuthService#revokeApiKey");
    }

    @Override
    public ApiKeyCreated rotateApiKey(long keyId, String idempotencyKey) {
        return NotYetImplemented.stub("AuthService#rotateApiKey");
    }

    // ---------- 内部工具 ----------

    private AuthSession toAuthSession(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return fromOidc(oidcUser, authentication);
        }
        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            return fromFormUser(userDetails, authentication);
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "无法识别的登录主体");
    }

    private AuthSession fromOidc(OidcUser oidcUser, Authentication authentication) {
        String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : "oidc";
        String subjectKey = issuer + "|" + oidcUser.getSubject();
        String displayName = oidcUser.getPreferredUsername() != null
                ? oidcUser.getPreferredUsername() : oidcUser.getName();
        List<String> scopes = authorities(authentication);
        // TODO(人工)：按 03-详细设计 将 subjectKey 映射到全局用户(sys_user)与激活租户，再从成员表取租户角色
        TenantContext defaultTenant = new TenantContext(1L, "default", "MEMBER");
        return new AuthSession(hashId(subjectKey), subjectKey, displayName,
                defaultTenant, List.of(defaultTenant), scopes);
    }

    private AuthSession fromFormUser(UserDetails userDetails, Authentication authentication) {
        String username = userDetails.getUsername();
        List<String> scopes = authorities(authentication);
        String tenantRole = scopes.stream()
                .map(scope -> scope.replace("ROLE_", ""))
                .findFirst()
                .orElse("MEMBER");
        TenantContext tenant = new TenantContext(1L, "default", tenantRole);
        return new AuthSession(hashId(username), "form|" + username, username,
                tenant, List.of(tenant), scopes);
    }

    private List<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    /** 稳定非负的本地用户标识（开发映射；真实 userId 由人工按用户表落库）。 */
    private long hashId(String key) {
        return Integer.toUnsignedLong(key.hashCode()) + 1;
    }
}
