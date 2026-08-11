package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.config.JwtTokenProperties;
import com.ragkb.service.modules.identity.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 签发/校验（form 模式开发认证链核心）。
 *
 * <p>HS256 签名，密钥来自 {@code RAGKB_JWT_SECRET}（启动时校验非空且 >= 32 字节，防弱密钥）。
 * 载荷严格校验 iss/aud/exp/nbf/jti/token type：
 * <ul>
 *   <li>access：{@code typ=access}，携带 {@code uid}、{@code sub}(subjectKey)、{@code scp}、{@code rls}、{@code ten}；</li>
 *   <li>refresh：{@code typ=refresh}，携带 {@code uid}/{@code sub}/{@code ten} + {@code rfid}(refreshFamilyId)，
 *       不固化角色（角色在轮换时由身份目录重新解析）。</li>
 * </ul>
 * 解析失败统一抛 {@link ApiException}{@code UNAUTHORIZED}；{@link #accessJti} 宽容读取（登出黑名单用）。
 */
@Service
public class TokenServiceImpl implements TokenService {

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String AUDIENCE = "ragkb:web";

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_SCOPE = "scp";
    private static final String CLAIM_ROLES = "rls";
    private static final String CLAIM_TENANT = "ten";
    private static final String CLAIM_FAMILY = "rfid";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public TokenServiceImpl(JwtTokenProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "RAGKB_JWT_SECRET 必须配置且编码后 >= 32 字节（随机生成，禁止硬编码或弱密钥）");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer() != null && !properties.issuer().isBlank()
                ? properties.issuer() : "ragkb";
        this.accessTtl = properties.accessTtl() != null ? properties.accessTtl() : Duration.ofMinutes(15);
        this.refreshTtl = properties.refreshTtl() != null ? properties.refreshTtl() : Duration.ofDays(30);
    }

    @Override
    public TokenPair issue(long userId, String subjectKey, List<String> scopes,
                           List<String> tenantRoles, long tenantId) {
        Instant now = Instant.now();
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        return pair(now, userId, subjectKey, scopes, tenantRoles, tenantId, familyId, accessJti, refreshJti);
    }

    @Override
    public TokenPair issueRotated(long userId, String subjectKey, List<String> scopes,
                                  List<String> tenantRoles, long tenantId,
                                  String familyId, String refreshJti) {
        Instant now = Instant.now();
        String accessJti = UUID.randomUUID().toString();
        return pair(now, userId, subjectKey, scopes, tenantRoles, tenantId, familyId, accessJti, refreshJti);
    }

    private TokenPair pair(Instant now, long userId, String subjectKey, List<String> scopes,
                           List<String> tenantRoles, long tenantId, String familyId,
                           String accessJti, String refreshJti) {
        String accessToken = build(accessJti, subjectKey, userId, scopes, tenantRoles, tenantId,
                null, TOKEN_TYPE_ACCESS, accessTtl, now);
        String refreshToken = build(refreshJti, subjectKey, userId, List.of(), List.of(), tenantId,
                familyId, TOKEN_TYPE_REFRESH, refreshTtl, now);
        return new TokenPair(accessToken, refreshToken, familyId, refreshJti,
                now.plus(accessTtl), now.plus(refreshTtl));
    }

    @Override
    public JwtPrincipal parseAccess(String accessToken) {
        Claims claims = parse(accessToken, TOKEN_TYPE_ACCESS);
        return toPrincipal(claims, false);
    }

    @Override
    public JwtPrincipal parseRefresh(String refreshToken) {
        Claims claims = parse(refreshToken, TOKEN_TYPE_REFRESH);
        return toPrincipal(claims, true);
    }

    @Override
    public String accessJti(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            return parser().build().parseSignedClaims(accessToken).getPayload().getId();
        } catch (ExpiredJwtException e) {
            // 宽容：已过期但签名有效的 access token 仍可取 jti 加入黑名单（防重放）
            return e.getClaims().getId();
        } catch (JwtException | IllegalArgumentException e) {
            // 签名无效 / 格式错误：登出幂等，忽略
            return null;
        }
    }

    @Override
    public Duration accessTtl() {
        return accessTtl;
    }

    @Override
    public Duration refreshTtl() {
        return refreshTtl;
    }

    // ---------- 内部工具 ----------

    private String build(String jti, String subject, long userId, List<String> scopes,
                         List<String> tenantRoles, long tenantId, String refreshFamilyId,
                         String tokenType, Duration ttl, Instant now) {
        JwtBuilder builder = Jwts.builder()
                .id(jti)
                .subject(subject)
                .issuer(issuer)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(CLAIM_TYPE, tokenType)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_TENANT, tenantId)
                .signWith(signingKey);
        if (scopes != null && !scopes.isEmpty()) {
            builder.claim(CLAIM_SCOPE, scopes);
        }
        if (tenantRoles != null && !tenantRoles.isEmpty()) {
            builder.claim(CLAIM_ROLES, tenantRoles);
        }
        if (refreshFamilyId != null) {
            builder.claim(CLAIM_FAMILY, refreshFamilyId);
        }
        return builder.compact();
    }

    /** 严格解析：签名 + iss/aud/typ + exp/nbf（jjwt 默认校验过期与生效时间）。 */
    private Claims parse(String token, String expectedType) {
        try {
            Jws<Claims> jws = parser()
                    .requireIssuer(issuer)
                    .requireAudience(AUDIENCE)
                    .require(CLAIM_TYPE, expectedType)
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "凭证无效或已过期", e);
        }
    }

    private JwtParserBuilder parser() {
        return Jwts.parser().verifyWith(signingKey);
    }

    private JwtPrincipal toPrincipal(Claims claims, boolean isRefresh) {
        Long uid = claims.get(CLAIM_UID, Long.class);
        Long tenantId = claims.get(CLAIM_TENANT, Long.class);
        if (uid == null || tenantId == null || claims.getId() == null || claims.getSubject() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "凭证载荷不完整");
        }
        List<String> scopes = readStringList(claims.get(CLAIM_SCOPE));
        List<String> roles = readStringList(claims.get(CLAIM_ROLES));
        String familyId = isRefresh ? claims.get(CLAIM_FAMILY, String.class) : null;
        if (isRefresh && (familyId == null || familyId.isBlank())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "refresh 凭证缺少家族标识");
        }
        return new JwtPrincipal(uid, claims.getSubject(), claims.getId(), familyId, scopes, roles, tenantId);
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "凭证载荷类型错误");
    }
}
