package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.config.JwtTokenProperties;
import com.ragkb.service.modules.access.service.PermissionCatalog;
import com.ragkb.service.modules.identity.adapter.ApiKeyCrypto;
import com.ragkb.service.modules.identity.dto.ApiKeyCreateDto;
import com.ragkb.service.modules.identity.port.ApiKeyStorePort;
import com.ragkb.service.modules.identity.port.IdentityDirectory;
import com.ragkb.service.modules.identity.port.RefreshTokenStorePort;
import com.ragkb.service.modules.identity.port.TokenBlacklistPort;
import com.ragkb.service.modules.identity.service.AuthService;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.identity.vo.ApiKeyCreatedVo;
import com.ragkb.service.modules.identity.vo.ApiKeyVo;
import com.ragkb.service.modules.identity.vo.AuthSessionVo;
import com.ragkb.service.modules.identity.vo.TenantContextVo;
import com.ragkb.service.modules.identity.vo.TokenResponseVo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证会话用例（form 模式全链路）。
 *
 * <p>登录/刷新/登出/切换租户都经 {@link IdentityDirectory} 获取当前真实成员关系，
 * 不信任客户端自报租户/角色；角色由 {@link PermissionCatalog} 聚合成权限视图返回给前端。
 * API Key 管理委托 {@link ApiKeyStorePort}（无 DB 时返回明确错误）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** form 登录的凭证能力（区别于租户角色与最终权限）。 */
    private static final String CREDENTIAL_SCOPE_WEB = "web";

    private final String authMode;
    private final JwtTokenProperties jwtProperties;
    private final TokenService tokenService;
    private final TokenBlacklistPort blacklistPort;
    private final RefreshTokenStorePort refreshStore;
    private final IdentityDirectory identityDirectory;
    private final PermissionCatalog permissionCatalog;
    private final ApiKeyCrypto apiKeyCrypto;
    private final ObjectProvider<ApiKeyStorePort> apiKeyStoreProvider;

    /** 进程内幂等去重（{@code tenantId:operation:key} → seen）。⚠️ 全量幂等落 idempotency_record 为人工实现点。 */
    private final Map<String, Boolean> idempotencySeen = new ConcurrentHashMap<>();

    public AuthServiceImpl(
            @Value("${ragkb.auth.mode:form}") String authMode,
            JwtTokenProperties jwtProperties,
            TokenService tokenService,
            TokenBlacklistPort blacklistPort,
            RefreshTokenStorePort refreshStore,
            IdentityDirectory identityDirectory,
            PermissionCatalog permissionCatalog,
            ApiKeyCrypto apiKeyCrypto,
            ObjectProvider<ApiKeyStorePort> apiKeyStoreProvider) {
        this.authMode = authMode;
        this.jwtProperties = jwtProperties;
        this.tokenService = tokenService;
        this.blacklistPort = blacklistPort;
        this.refreshStore = refreshStore;
        this.identityDirectory = identityDirectory;
        this.permissionCatalog = permissionCatalog;
        this.apiKeyCrypto = apiKeyCrypto;
        this.apiKeyStoreProvider = apiKeyStoreProvider;
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
        // OIDC 回调由 Spring Security oauth2Login 完成；会话补充逻辑（首次登录建号）为人工实现点。
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
        IdentityDirectory.ResolvedIdentity identity = requireIdentity(resolveSubjectKey(authentication));
        IdentityDirectory.TenantMembership active = firstActiveMembership(identity.userId());
        return issueAuthResult(identity, active);
    }

    @Override
    public AuthResult refresh(String rawRefreshToken) {
        TokenService.JwtPrincipal refresh = tokenService.parseRefresh(rawRefreshToken);
        String familyId = refresh.refreshFamilyId();
        String newRefreshJti = UUID.randomUUID().toString();
        boolean rotated = refreshStore.verifyAndRotate(
                familyId, refresh.jti(), newRefreshJti, tokenService.refreshTtl());
        if (!rotated) {
            // 旧 refresh 被复用（疑似被盗）：Store 已原子吊销整族
            throw new ApiException(ErrorCode.UNAUTHORIZED, "刷新凭证已失效，请重新登录");
        }
        IdentityDirectory.ResolvedIdentity identity = requireIdentity(refresh.subjectKey());
        IdentityDirectory.TenantMembership active = identityDirectory.membership(identity.userId(), refresh.tenantId())
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "租户成员关系已变更，请重新登录"));
        TokenService.TokenPair pair = tokenService.issueRotated(
                identity.userId(), identity.subjectKey(), List.of(CREDENTIAL_SCOPE_WEB),
                active.roles(), active.tenantId(), familyId, newRefreshJti);
        return toAuthResult(pair, buildSession(identity, active));
    }

    @Override
    public void logout(String accessToken, String rawRefreshToken) {
        // access 黑名单：宽容读取 jti（已过期也能取），TTL 用 accessTtl 兜底（安全，不长于 token 寿命）
        String jti = tokenService.accessJti(accessToken);
        if (jti != null) {
            blacklistPort.blacklist(jti, tokenService.accessTtl());
        }
        // refresh 家族吊销（幂等：凭证无效则忽略）
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            try {
                refreshStore.revoke(tokenService.parseRefresh(rawRefreshToken).refreshFamilyId());
            } catch (ApiException ignored) {
                // 幂等登出：无效 refresh 无需吊销
            }
        }
    }

    @Override
    public AuthResult switchTenant(long tenantId) {
        TokenService.JwtPrincipal principal = currentPrincipal();
        IdentityDirectory.ResolvedIdentity identity = requireIdentity(principal.subjectKey());
        IdentityDirectory.TenantMembership active = identityDirectory.membership(identity.userId(), tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "无权切换到该租户"));
        // JWT 模式：重签含新 tenantId 的 access + 新家族 refresh（旧上下文随新 token 失效）
        TokenService.TokenPair pair = tokenService.issue(
                identity.userId(), identity.subjectKey(),
                principal.scopes().isEmpty() ? List.of(CREDENTIAL_SCOPE_WEB) : principal.scopes(),
                active.roles(), active.tenantId());
        refreshStore.save(pair.refreshFamilyId(), pair.refreshJti(), tokenService.refreshTtl());
        return toAuthResult(pair, buildSession(identity, active));
    }

    // ---------- API Key（委托 ApiKeyStorePort；无 DB 时明确报错） ----------

    @Override
    public List<ApiKeyVo> listApiKeys() {
        ApiKeyStorePort store = requireApiKeyStore();
        long tenantId = currentPrincipal().tenantId();
        return store.list(tenantId).stream().map(this::toApiKeyVo).toList();
    }

    @Override
    public ApiKeyCreatedVo createApiKey(ApiKeyCreateDto request, String idempotencyKey) {
        ApiKeyStorePort store = requireApiKeyStore();
        TokenService.JwtPrincipal principal = currentPrincipal();
        guardIdempotency(principal.tenantId(), "createApiKey", idempotencyKey);
        String raw = apiKeyCrypto.generateSecret();
        long keyId = store.create(new ApiKeyStorePort.CreateCommand(
                principal.tenantId(), request.name(), request.scopes(), request.allowedKbIds(),
                request.expiresAt(), principal.userId(),
                apiKeyCrypto.digest(raw), apiKeyCrypto.prefix(raw), 60));
        ApiKeyStorePort.ApiKeyRecord record = store.findById(principal.tenantId(), keyId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "API Key 创建后读取失败"));
        return new ApiKeyCreatedVo(toApiKeyVo(record), raw);
    }

    @Override
    public void revokeApiKey(long keyId) {
        ApiKeyStorePort store = requireApiKeyStore();
        long tenantId = currentPrincipal().tenantId();
        store.findById(tenantId, keyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "API Key 不存在"));
        store.revoke(tenantId, keyId);
    }

    @Override
    public ApiKeyCreatedVo rotateApiKey(long keyId, String idempotencyKey) {
        ApiKeyStorePort store = requireApiKeyStore();
        long tenantId = currentPrincipal().tenantId();
        store.findById(tenantId, keyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "API Key 不存在"));
        String raw = apiKeyCrypto.generateSecret();
        store.updateDigestAndPrefix(tenantId, keyId, apiKeyCrypto.digest(raw), apiKeyCrypto.prefix(raw));
        ApiKeyStorePort.ApiKeyRecord record = store.findById(tenantId, keyId).orElseThrow();
        return new ApiKeyCreatedVo(toApiKeyVo(record), raw);
    }

    // ---------- 内部工具 ----------

    private ApiKeyStorePort requireApiKeyStore() {
        ApiKeyStorePort store = apiKeyStoreProvider.getIfAvailable();
        if (store == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "API Key 需要启用数据库（RAGKB_DB_ENABLED=true）");
        }
        return store;
    }

    private void guardIdempotency(long tenantId, String operation, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        String dedupeKey = tenantId + ":" + operation + ":" + idempotencyKey;
        if (idempotencySeen.putIfAbsent(dedupeKey, Boolean.TRUE) != null) {
            throw new ApiException(ErrorCode.CONFLICT, "重复的幂等请求，请使用新幂等键");
        }
    }

    private ApiKeyVo toApiKeyVo(ApiKeyStorePort.ApiKeyRecord record) {
        return new ApiKeyVo(record.id(), record.name(), record.keyPrefix(), record.scopes(),
                record.allowedKbIds(), record.status(), record.expiresAt(), record.lastUsedAt(),
                record.createdAt());
    }

    private TokenService.JwtPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
        }
        return principal;
    }

    private AuthResult issueAuthResult(IdentityDirectory.ResolvedIdentity identity,
                                       IdentityDirectory.TenantMembership active) {
        TokenService.TokenPair pair = tokenService.issue(
                identity.userId(), identity.subjectKey(), List.of(CREDENTIAL_SCOPE_WEB),
                active.roles(), active.tenantId());
        refreshStore.save(pair.refreshFamilyId(), pair.refreshJti(), tokenService.refreshTtl());
        return toAuthResult(pair, buildSession(identity, active));
    }

    private AuthResult toAuthResult(TokenService.TokenPair pair, AuthSessionVo session) {
        long expiresIn = Math.max(0, pair.accessExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
        TokenResponseVo response = new TokenResponseVo(pair.accessToken(), "Bearer", expiresIn, session);
        return new AuthResult(response, pair.refreshToken(),
                Duration.ofSeconds(jwtProperties.refreshCookieMaxAgeSeconds()));
    }

    private AuthSessionVo buildSession(IdentityDirectory.ResolvedIdentity identity,
                                       IdentityDirectory.TenantMembership active) {
        List<TenantContextVo> tenants = identityDirectory.memberships(identity.userId()).stream()
                .map(member -> new TenantContextVo(member.tenantId(), member.tenantCode(), member.roles()))
                .toList();
        TenantContextVo activeTenant = new TenantContextVo(active.tenantId(), active.tenantCode(), active.roles());
        Set<String> permissionSet = permissionCatalog.permissionsForRoles(active.roles());
        List<String> permissions = new ArrayList<>(permissionSet);
        List<String> features = new ArrayList<>(permissionCatalog.featuresFor(permissionSet));
        return new AuthSessionVo(
                identity.userId(),
                identity.subjectKey(),
                identity.displayName(),
                activeTenant,
                tenants,
                active.roles(),
                List.of(CREDENTIAL_SCOPE_WEB),
                permissions,
                features,
                active.policyVersion());
    }

    private AuthSessionVo toAuthSession(Authentication authentication) {
        IdentityDirectory.ResolvedIdentity identity = requireIdentity(resolveSubjectKey(authentication));
        long tenantId = activeTenantIdOf(authentication, identity);
        IdentityDirectory.TenantMembership active = identityDirectory.membership(identity.userId(), tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "租户成员关系已变更，请重新登录"));
        return buildSession(identity, active);
    }

    private long activeTenantIdOf(Authentication authentication, IdentityDirectory.ResolvedIdentity identity) {
        if (authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal) {
            return principal.tenantId();
        }
        return firstActiveMembership(identity.userId()).tenantId();
    }

    private IdentityDirectory.TenantMembership firstActiveMembership(long userId) {
        return identityDirectory.memberships(userId).stream()
                .filter(member -> "ACTIVE".equals(member.status()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "当前无可用租户"));
    }

    private IdentityDirectory.ResolvedIdentity requireIdentity(String subjectKey) {
        return identityDirectory.resolveBySubjectKey(subjectKey)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "用户不存在或已停用"));
    }

    private String resolveSubjectKey(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            String issuer = oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : "oidc";
            return issuer + "|" + oidcUser.getSubject();
        }
        if (principal instanceof UserDetails userDetails) {
            return "form|" + userDetails.getUsername();
        }
        if (principal instanceof TokenService.JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.subjectKey();
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "无法识别的登录主体");
    }
}
