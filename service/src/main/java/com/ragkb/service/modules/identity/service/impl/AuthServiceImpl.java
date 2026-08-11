package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.modules.identity.vo.AuthSessionVo;
import com.ragkb.service.modules.identity.vo.ApiKeyVo;
import com.ragkb.service.modules.identity.vo.ApiKeyCreatedVo;
import com.ragkb.service.modules.identity.dto.ApiKeyCreateDto;
import com.ragkb.service.modules.identity.vo.TenantContextVo;
import com.ragkb.service.modules.identity.vo.TokenResponseVo;
import com.ragkb.service.modules.identity.port.RefreshTokenStorePort;
import com.ragkb.service.modules.identity.port.TokenBlacklistPort;
import com.ragkb.service.modules.identity.service.AuthService;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.util.TodoSupport;
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
 * <p>基础设施部分（会话概览/登出钩子/授权地址）基于 Spring Security 实现；真实用户体系、
 * 租户映射与 JWT 签发/Redis 吊销由人工实现（见各方法 TODO）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final String authMode;
    private final TokenService tokenService;
    private final TokenBlacklistPort blacklistPort;
    private final RefreshTokenStorePort refreshStore;

    public AuthServiceImpl(
            @Value("${ragkb.auth.mode:form}") String authMode,
            TokenService tokenService,
            TokenBlacklistPort blacklistPort,
            RefreshTokenStorePort refreshStore) {
        this.authMode = authMode;
        this.tokenService = tokenService;
        this.blacklistPort = blacklistPort;
        this.refreshStore = refreshStore;
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
    public AuthSessionVo session() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
        }
        return toAuthSession(authentication);
    }

    @Override
    public AuthResult login(Authentication authentication) {
        // TODO(人工)：组装 TokenService.issue 与 session() 视图；refresh token 返回给 Controller 写 cookie。
        return TodoSupport.notImplemented("AuthService#login");
    }

    @Override
    public AuthResult refresh(String rawRefreshToken) {
        // TODO(人工)：TokenService.parseRefresh + RefreshTokenStorePort.verifyAndRotate（复用检测），
        // 失败吊销整族并抛 UNAUTHORIZED。
        return TodoSupport.notImplemented("AuthService#refresh");
    }

    @Override
    public void logout(String accessToken, String rawRefreshToken) {
        // TODO(人工)：TokenService.accessJti + blacklistPort.blacklist(jti, 剩余TTL)；
        // refresh 家族吊销 refreshStore.revoke(familyId)。
        TodoSupport.notImplemented("AuthService#logout");
    }

    @Override
    public AuthSessionVo switchTenant(long tenantId) {
        // TODO 按 03-详细设计 §4 校验租户成员后切换激活租户。
        return TodoSupport.notImplemented("AuthService#switchTenant");
    }

    // ---------- API Key（人工实现落库与签名校验） ----------

    @Override
    public List<ApiKeyVo> listApiKeys() {
        return TodoSupport.notImplemented("AuthService#listApiKeys");
    }

    @Override
    public ApiKeyCreatedVo createApiKey(ApiKeyCreateDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("AuthService#createApiKey");
    }

    @Override
    public void revokeApiKey(long keyId) {
        TodoSupport.notImplemented("AuthService#revokeApiKey");
    }

    @Override
    public ApiKeyCreatedVo rotateApiKey(long keyId, String idempotencyKey) {
        return TodoSupport.notImplemented("AuthService#rotateApiKey");
    }

    // ---------- 内部工具 ----------

    private AuthSessionVo toAuthSession(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return fromOidc(oidcUser, authentication);
        }
        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            return fromFormUser(userDetails, authentication);
        }
        if (authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal) {
            return authSessionFromJwt(principal, authentication);
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "无法识别的登录主体");
    }

    private AuthSessionVo fromOidc(OidcUser oidcUser, Authentication authentication) {
        String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : "oidc";
        String subjectKey = issuer + "|" + oidcUser.getSubject();
        String displayName = oidcUser.getPreferredUsername() != null
                ? oidcUser.getPreferredUsername() : oidcUser.getName();
        List<String> scopes = authorities(authentication);
        // TODO(人工)：按 03-详细设计 将 subjectKey 映射到全局用户(sys_user)与激活租户，再从成员表取租户角色
        TenantContextVo defaultTenant = new TenantContextVo(1L, "default", "MEMBER");
        return new AuthSessionVo(hashId(subjectKey), subjectKey, displayName,
                defaultTenant, List.of(defaultTenant), scopes);
    }

    private AuthSessionVo fromFormUser(UserDetails userDetails, Authentication authentication) {
        String username = userDetails.getUsername();
        List<String> scopes = authorities(authentication);
        String tenantRole = scopes.stream()
                .map(scope -> scope.replace("ROLE_", ""))
                .findFirst()
                .orElse("MEMBER");
        TenantContextVo tenant = new TenantContextVo(1L, "default", tenantRole);
        return new AuthSessionVo(hashId(username), "form|" + username, username,
                tenant, List.of(tenant), scopes);
    }

    /**
     * JWT 主体 → 会话视图（TODO 桩，人工实现）。
     *
     * <p>人工实现点：将 {@link TokenService.JwtPrincipal} 的 userId/subjectKey 映射到全局用户与
     * 激活租户，再从成员表取租户角色；骨架不固化 scopes→角色的信任边界（见交付说明）。
     */
    private AuthSessionVo authSessionFromJwt(TokenService.JwtPrincipal principal, Authentication authentication) {
        return TodoSupport.notImplemented("AuthServiceImpl#authSessionFromJwt");
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
