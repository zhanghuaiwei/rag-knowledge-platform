package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.config.JwtTokenProperties;
import com.ragkb.service.modules.identity.service.TokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceImplTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef"; // >= 32 字节

    private TokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        JwtTokenProperties properties = new JwtTokenProperties(
                SECRET, "ragkb", Duration.ofMinutes(15), Duration.ofDays(30), 2592000);
        tokenService = new TokenServiceImpl(properties);
    }

    @Test
    void issueAndParseRoundTrip() {
        TokenService.TokenPair pair = tokenService.issue(
                1L, "form|admin", List.of("web"), List.of("TENANT_ADMIN"), 1L);

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertNotNull(pair.refreshFamilyId());

        TokenService.JwtPrincipal access = tokenService.parseAccess(pair.accessToken());
        assertEquals(1L, access.userId());
        assertEquals("form|admin", access.subjectKey());
        assertEquals(List.of("web"), access.scopes());
        assertEquals(List.of("TENANT_ADMIN"), access.tenantRoles());
        assertEquals(1L, access.tenantId());
        assertNotNull(access.jti());
        assertNull(access.refreshFamilyId());

        TokenService.JwtPrincipal refresh = tokenService.parseRefresh(pair.refreshToken());
        assertEquals(pair.refreshFamilyId(), refresh.refreshFamilyId());
        assertEquals(1L, refresh.tenantId());
        assertEquals(List.of(), refresh.scopes()); // refresh 不固化角色/scope
    }

    @Test
    void parseAccessRejectsRefreshToken() {
        TokenService.TokenPair pair = tokenService.issue(1L, "form|admin", List.of("web"), List.of(), 1L);
        assertThrows(ApiException.class, () -> tokenService.parseAccess(pair.refreshToken()));
    }

    @Test
    void parseRefreshRejectsAccessToken() {
        TokenService.TokenPair pair = tokenService.issue(1L, "form|admin", List.of("web"), List.of(), 1L);
        assertThrows(ApiException.class, () -> tokenService.parseRefresh(pair.accessToken()));
    }

    @Test
    void expiredAccessTokenRejected() {
        String expired = Jwts.builder()
                .id("jti-expired")
                .subject("form|admin")
                .issuer("ragkb")
                .audience().add("ragkb:web").and()
                .expiration(Date.from(Instant.now().minusSeconds(10)))
                .claim("typ", "access")
                .claim("uid", 1L)
                .claim("ten", 1L)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThrows(ApiException.class, () -> tokenService.parseAccess(expired));
    }

    @Test
    void wrongSignatureRejected() {
        TokenService.TokenPair pair = tokenService.issue(1L, "form|admin", List.of("web"), List.of(), 1L);
        TokenServiceImpl attacker = new TokenServiceImpl(new JwtTokenProperties(
                "another-secret-0123456789abcdef0123456789abcdef", "ragkb",
                Duration.ofMinutes(15), Duration.ofDays(30), 2592000));
        assertThrows(ApiException.class, () -> attacker.parseAccess(pair.accessToken()));
    }

    @Test
    void wrongIssuerRejected() {
        TokenService.TokenPair pair = tokenService.issue(1L, "form|admin", List.of("web"), List.of(), 1L);
        TokenServiceImpl otherIssuer = new TokenServiceImpl(new JwtTokenProperties(
                SECRET, "other-issuer", Duration.ofMinutes(15), Duration.ofDays(30), 2592000));
        assertThrows(ApiException.class, () -> otherIssuer.parseAccess(pair.accessToken()));
    }

    @Test
    void accessJtiIsLenient() {
        TokenService.TokenPair pair = tokenService.issue(1L, "form|admin", List.of("web"), List.of(), 1L);
        assertNotNull(tokenService.accessJti(pair.accessToken()));

        // 已过期但签名有效：仍可读取 jti（登出黑名单场景）
        String expired = Jwts.builder()
                .id("jti-expired-lenient")
                .subject("form|admin")
                .issuer("ragkb")
                .audience().add("ragkb:web").and()
                .expiration(Date.from(Instant.now().minusSeconds(10)))
                .claim("typ", "access")
                .claim("uid", 1L)
                .claim("ten", 1L)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertEquals("jti-expired-lenient", tokenService.accessJti(expired));

        // 垃圾输入：返回 null（幂等登出容忍）
        assertNull(tokenService.accessJti("not-a-jwt"));
        assertNull(tokenService.accessJti(null));
    }

    @Test
    void weakSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new TokenServiceImpl(
                new JwtTokenProperties("short", "ragkb", Duration.ofMinutes(15), Duration.ofDays(30), 2592000)));
    }

    @Test
    void ttlExposed() {
        assertEquals(Duration.ofMinutes(15), tokenService.accessTtl());
        assertEquals(Duration.ofDays(30), tokenService.refreshTtl());
        assertTrue(tokenService.accessTtl().compareTo(tokenService.refreshTtl()) < 0);
    }
}
