package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.config.JwtTokenProperties;
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
import com.ragkb.service.modules.identity.vo.AuthSessionVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Duration COOKIE_MAX_AGE = Duration.ofSeconds(2592000);

    @Mock private TokenService tokenService;
    @Mock private TokenBlacklistPort blacklistPort;
    @Mock private RefreshTokenStorePort refreshStore;
    @Mock private IdentityDirectory identityDirectory;
    @Mock private ObjectProvider<ApiKeyStorePort> apiKeyStoreProvider;
    @Mock private ObjectProvider<UserCredentialStorePort> credentialStoreProvider;
    @Mock private ObjectProvider<org.springframework.security.crypto.password.PasswordEncoder> passwordEncoderProvider;

    private final JwtTokenProperties properties = new JwtTokenProperties(
            "test-secret-0123456789abcdef0123456789abcdef", "ragkb",
            Duration.ofMinutes(15), REFRESH_TTL, 2592000);
    private final PermissionCatalog catalog = new PermissionCatalog();
    private final ApiKeyCrypto apiKeyCrypto = new ApiKeyCrypto("test-pepper");

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl("form", properties, tokenService, blacklistPort,
                refreshStore, identityDirectory, catalog, apiKeyCrypto, apiKeyStoreProvider,
                credentialStoreProvider, passwordEncoderProvider);
        // 部分用例不调用 refreshTtl，lenient 避免 StrictStubs 误报
        lenient().when(tokenService.refreshTtl()).thenReturn(REFRESH_TTL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- 辅助 ----------

    private IdentityDirectory.ResolvedIdentity devIdentity() {
        return new IdentityDirectory.ResolvedIdentity(1L, "form|admin", "admin", "admin@ragkb.dev");
    }

    private IdentityDirectory.TenantMembership adminTenant() {
        return new IdentityDirectory.TenantMembership(1L, "default", "默认租户", "ACTIVE",
                List.of("TENANT_ADMIN"), 1L);
    }

    private TokenService.TokenPair pair(String family) {
        Instant now = Instant.now();
        return new TokenService.TokenPair("at-" + family, "rt-" + family, family, "rj-" + family,
                now.plusSeconds(900), now.plus(REFRESH_TTL));
    }

    private void setJwtContext(long tenantId, List<String> roles) {
        var principal = new TokenService.JwtPrincipal(
                1L, "form|admin", "jti-x", null, List.of("web"), roles, tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "at", List.of()));
    }

    // ---------- login ----------

    @Test
    void loginIssuesTokensAndSavesRefreshFamily() {
        UserDetails user = User.withUsername("admin").password("admin123").roles("TENANT_ADMIN").build();
        Authentication auth = new UsernamePasswordAuthenticationToken(user, "admin123", user.getAuthorities());

        when(identityDirectory.resolveBySubjectKey("form|admin")).thenReturn(Optional.of(devIdentity()));
        when(identityDirectory.memberships(1L)).thenReturn(List.of(adminTenant()));
        when(tokenService.issue(1L, "form|admin", List.of("web"), List.of("TENANT_ADMIN"), 1L))
                .thenReturn(pair("f1"));

        AuthService.AuthResult result = service.login(auth);

        assertEquals("at-f1", result.response().accessToken());
        assertEquals("rt-f1", result.refreshToken());
        assertEquals(COOKIE_MAX_AGE, result.refreshCookieMaxAge());
        assertEquals("Bearer", result.response().tokenType());
        verify(refreshStore).save("f1", "rj-f1", REFRESH_TTL);

        AuthSessionVo session = result.response().session();
        assertEquals(List.of("TENANT_ADMIN"), session.tenantRoles());
        assertTrue(session.permissions().contains(PermissionCatalog.API_KEY_MANAGE));
        assertTrue(session.features().contains("governance"));
        assertEquals(1L, session.policyVersion());
        assertEquals(1L, session.activeTenant().tenantId());
    }

    @Test
    void loginUnknownUserDenied() {
        UserDetails user = User.withUsername("nobody").password("x").roles("MEMBER").build();
        Authentication auth = new UsernamePasswordAuthenticationToken(user, "x", user.getAuthorities());
        when(identityDirectory.resolveBySubjectKey("form|nobody")).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.login(auth));
    }

    // ---------- refresh ----------

    @Test
    void refreshRotatesWithinSameFamily() {
        var refresh = new TokenService.JwtPrincipal(
                1L, "form|admin", "old-jti", "family-1", List.of(), List.of(), 1L);
        when(tokenService.parseRefresh("rt")).thenReturn(refresh);
        when(refreshStore.verifyAndRotate(eq("family-1"), eq("old-jti"), anyString(), eq(REFRESH_TTL)))
                .thenReturn(true);
        when(identityDirectory.resolveBySubjectKey("form|admin")).thenReturn(Optional.of(devIdentity()));
        when(identityDirectory.membership(1L, 1L)).thenReturn(Optional.of(adminTenant()));
        when(tokenService.issueRotated(eq(1L), eq("form|admin"), anyList(),
                eq(List.of("TENANT_ADMIN")), eq(1L), eq("family-1"), anyString()))
                .thenReturn(pair("family-1"));

        AuthService.AuthResult result = service.refresh("rt");
        assertEquals("at-family-1", result.response().accessToken());
        assertEquals(1L, result.response().session().activeTenant().tenantId());
    }

    @Test
    void refreshReuseIsRejected() {
        var refresh = new TokenService.JwtPrincipal(
                1L, "form|admin", "stale-jti", "family-1", List.of(), List.of(), 1L);
        when(tokenService.parseRefresh("rt")).thenReturn(refresh);
        when(refreshStore.verifyAndRotate(eq("family-1"), eq("stale-jti"), anyString(), eq(REFRESH_TTL)))
                .thenReturn(false);
        assertThrows(ApiException.class, () -> service.refresh("rt"));
    }

    // ---------- logout ----------

    @Test
    void logoutBlacklistsAccessAndRevokesFamily() {
        when(tokenService.accessJti("at")).thenReturn("access-jti");
        when(tokenService.accessTtl()).thenReturn(Duration.ofMinutes(15));
        var refresh = new TokenService.JwtPrincipal(
                1L, "form|admin", "rf-jti", "family-2", List.of(), List.of(), 1L);
        when(tokenService.parseRefresh("rt")).thenReturn(refresh);

        service.logout("at", "rt");
        verify(blacklistPort).blacklist("access-jti", Duration.ofMinutes(15));
        verify(refreshStore).revoke("family-2");
    }

    @Test
    void logoutIsIdempotentWithMissingOrInvalidCredentials() {
        when(tokenService.accessJti(any())).thenReturn(null);
        when(tokenService.parseRefresh(any())).thenThrow(new ApiException(
                com.ragkb.service.common.exception.ErrorCode.UNAUTHORIZED));
        service.logout(null, "bad");
        service.logout("at", "bad");
        verify(blacklistPort, never()).blacklist(anyString(), any());
        verify(refreshStore, never()).revoke(anyString());
    }

    // ---------- session ----------

    @Test
    void sessionBuildsPermissionViewFromPrincipal() {
        setJwtContext(1L, List.of("TENANT_ADMIN"));
        when(identityDirectory.resolveBySubjectKey("form|admin")).thenReturn(Optional.of(devIdentity()));
        when(identityDirectory.memberships(1L)).thenReturn(List.of(adminTenant()));
        when(identityDirectory.membership(1L, 1L)).thenReturn(Optional.of(adminTenant()));

        AuthSessionVo session = service.session();
        assertEquals(List.of("TENANT_ADMIN"), session.tenantRoles());
        assertEquals(List.of("web"), session.credentialScopes());
        assertTrue(session.permissions().contains(PermissionCatalog.API_KEY_MANAGE));
        assertTrue(session.features().contains("governance"));
    }

    @Test
    void sessionWithoutAuthenticationThrowsUnauthorized() {
        assertThrows(ApiException.class, () -> service.session());
    }

    // ---------- switchTenant ----------

    @Test
    void switchTenantRequiresMembership() {
        setJwtContext(1L, List.of("TENANT_ADMIN"));
        when(identityDirectory.resolveBySubjectKey("form|admin")).thenReturn(Optional.of(devIdentity()));
        when(identityDirectory.membership(1L, 2L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.switchTenant(2L));
    }

    @Test
    void switchTenantReissuesTokensWithNewTenant() {
        setJwtContext(1L, List.of("TENANT_ADMIN"));
        when(identityDirectory.resolveBySubjectKey("form|admin")).thenReturn(Optional.of(devIdentity()));
        var memberTenant = new IdentityDirectory.TenantMembership(2L, "acme", "Acme", "ACTIVE",
                List.of("MEMBER"), 3L);
        when(identityDirectory.membership(1L, 2L)).thenReturn(Optional.of(memberTenant));
        when(tokenService.issue(1L, "form|admin", List.of("web"), List.of("MEMBER"), 2L))
                .thenReturn(pair("f2"));

        AuthService.AuthResult result = service.switchTenant(2L);
        assertEquals(2L, result.response().session().activeTenant().tenantId());
        assertEquals(List.of("MEMBER"), result.response().session().tenantRoles());
        assertEquals(3L, result.response().session().policyVersion());
        verify(refreshStore).save("f2", "rj-f2", REFRESH_TTL);
    }

    // ---------- API Key（无 DB 明确报错） ----------

    @Test
    void createApiKeyWithoutStoreThrowsClearError() {
        setJwtContext(1L, List.of("TENANT_ADMIN"));
        // apiKeyStoreProvider.getIfAvailable() 默认返回 null（无 DB 场景）
        ApiException ex = assertThrows(ApiException.class,
                () -> service.createApiKey(new ApiKeyCreateDto("my-key", List.of("web"), null, null), null));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("数据库"));
    }
}
