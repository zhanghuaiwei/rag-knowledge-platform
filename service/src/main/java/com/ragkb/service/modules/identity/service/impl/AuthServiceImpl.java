package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.config.JwtTokenProperties;
import com.ragkb.service.config.LocalAuthProperties;
import com.ragkb.service.modules.access.service.PermissionCatalog;
import com.ragkb.service.modules.identity.adapter.ApiKeyCrypto;
import com.ragkb.service.modules.identity.dto.ApiKeyCreateDto;
import com.ragkb.service.modules.identity.port.ApiKeyStorePort;
import com.ragkb.service.modules.identity.port.IdentityDirectory;
import com.ragkb.service.modules.identity.port.RefreshTokenStorePort;
import com.ragkb.service.modules.identity.port.TokenBlacklistPort;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    /** 本地账号凭据策略配置（改密后按 passwordExpiryDays 重算过期时间；<=0 表示不过期）。 */
    private final LocalAuthProperties localAuthProperties;
    private final TokenService tokenService;
    private final TokenBlacklistPort blacklistPort;
    private final RefreshTokenStorePort refreshStore;
    private final IdentityDirectory identityDirectory;
    private final PermissionCatalog permissionCatalog;
    private final ApiKeyCrypto apiKeyCrypto;
    private final ObjectProvider<ApiKeyStorePort> apiKeyStoreProvider;
    private final ObjectProvider<UserCredentialStorePort> credentialStoreProvider;
    private final ObjectProvider<PasswordEncoder> passwordEncoderProvider;

    /** 进程内幂等去重（{@code tenantId:operation:key} → seen）。⚠️ 全量幂等落 idempotency_record 为人工实现点。 */
    private final Map<String, Boolean> idempotencySeen = new ConcurrentHashMap<>();

    public AuthServiceImpl(
            @Value("${ragkb.auth.mode:form}") String authMode,
            JwtTokenProperties jwtProperties,
            LocalAuthProperties localAuthProperties,
            TokenService tokenService,
            TokenBlacklistPort blacklistPort,
            RefreshTokenStorePort refreshStore,
            IdentityDirectory identityDirectory,
            PermissionCatalog permissionCatalog,
            ApiKeyCrypto apiKeyCrypto,
            ObjectProvider<ApiKeyStorePort> apiKeyStoreProvider,
            ObjectProvider<UserCredentialStorePort> credentialStoreProvider,
            ObjectProvider<PasswordEncoder> passwordEncoderProvider) {
        this.authMode = authMode;
        this.jwtProperties = jwtProperties;
        this.localAuthProperties = localAuthProperties;
        this.tokenService = tokenService;
        this.blacklistPort = blacklistPort;
        this.refreshStore = refreshStore;
        this.identityDirectory = identityDirectory;
        this.permissionCatalog = permissionCatalog;
        this.apiKeyCrypto = apiKeyCrypto;
        this.apiKeyStoreProvider = apiKeyStoreProvider;
        this.credentialStoreProvider = credentialStoreProvider;
        this.passwordEncoderProvider = passwordEncoderProvider;
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
        CredentialPolicyFlags flags = credentialPolicyFlags(identity.userId());
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
                active.policyVersion(),
                flags.mustChangePassword(),
                flags.passwordExpired());
    }

    /**
     * 会话携带本地凭据策略标志（form+db 模式才有值；oidc/无 DB 返回 null）。
     *
     * <p>⚠️ 谨慎区（人工复核）：密码过期判定（{@code password_expires_at < now}）与
     * {@code ragkb.auth.local.password-expiry-days > 0} 是否启用、以及
     * {@code CredentialPolicyGateFilter} 的门禁一致性，需人工确认。
     */
    private CredentialPolicyFlags credentialPolicyFlags(long userId) {
        UserCredentialStorePort store = credentialStoreProvider.getIfAvailable();
        if (store == null) {
            // 无本地凭据存储（oidc 模式或无 DB）：本地账号凭据策略不适用
            return new CredentialPolicyFlags(null, null);
        }
        return store.findByUserId(userId)
                .map(cred -> new CredentialPolicyFlags(
                        cred.mustChangePassword(),
                        cred.passwordExpiresAt() != null && cred.passwordExpiresAt().isBefore(Instant.now())))
                .orElseGet(() -> new CredentialPolicyFlags(null, null));
    }

    private record CredentialPolicyFlags(Boolean mustChangePassword, Boolean passwordExpired) {
    }

    @Override
    public void changePassword(long userId, String currentPassword, String newPassword) {
        // 自助改密仅 form+db 模式可用：无本地凭据存储（oidc 部署或未启用 DB）直接明确报错
        UserCredentialStorePort credentialStore = credentialStoreProvider.getIfAvailable();
        if (credentialStore == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "改密需要启用数据库与 form 模式");
        }
        // BCrypt 编码器同样依赖 form 模式装配，缺失即功能不可用（防御性，正常容器必注入）
        PasswordEncoder passwordEncoder = passwordEncoderProvider.getIfAvailable();
        if (passwordEncoder == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "改密需要启用数据库与 form 模式");
        }
        // 按全局用户 id 重读凭据（form 模式下一人至多一条本地凭据）
        UserCredentialStorePort.CredentialRecord credential = credentialStore.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "用户不存在"));
        // 核验当前密码：与库中 BCrypt 哈希比对，失败即拒绝（防会话被窃后改密接管账号）
        if (!passwordEncoder.matches(currentPassword, credential.passwordHash())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "当前密码错误");
        }
        // 新密码统一强度策略（>=8 位且含字母数字，与建号/重置同一份 PasswordPolicy）
        PasswordPolicy.requireStrong(newPassword);
        // 改密时间起点 = now；过期时间按策略配置重算（<=0 表示未启用过期，写 NULL）
        Instant now = Instant.now();
        Instant passwordExpiresAt = localAuthProperties.passwordExpiryDays() > 0
                ? now.plus(Duration.ofDays(localAuthProperties.passwordExpiryDays()))
                : null;
        // 自助改密成功：must_change_password 置 false（经 CredentialPolicyGateFilter 每请求
        // 重读 DB 自然解除门禁）；同时清失败计数/锁定，凭据恢复健康态。
        // ⚠️ 刻意不清当前会话 refresh 家族：自助改密不强制重新登录（既定契约，
        // 旧 access 最长 15 分钟自然过期；按用户吊销全量会话需引入 per-user 会话版本，列为后续演进）。
        credentialStore.updatePassword(credential.id(), passwordEncoder.encode(newPassword),
                now, passwordExpiresAt, false);
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
