package com.ragkb.service.modules.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * JWT token 生命周期用例（实现点由人工完成；当前为接口契约）。
 *
 * <p>access token 短命（默认 15m），前端仅内存持有；refresh token 长命（默认 30d），
 * 只存在于 HttpOnly cookie，轮换 + 复用检测（见
 * {@link com.ragkb.service.modules.identity.port.RefreshTokenStorePort}）。
 */
public interface TokenService {

    /** access/refresh 统一载荷视图；access token 无 {@code refreshFamilyId}。 */
    record JwtPrincipal(long userId, String subjectKey, String jti,
                        String refreshFamilyId, List<String> scopes, long tenantId) {
    }

    /** 一次签发的 access + refresh + 家族标识（refresh 家族复用 access 的 jti 或独立随机）。 */
    record TokenPair(String accessToken, String refreshToken, String refreshFamilyId,
                     Instant accessExpiresAt, Instant refreshExpiresAt) {
    }

    /** 签发 access + refresh 令牌对。 */
    TokenPair issue(long userId, String subjectKey, List<String> scopes, long tenantId);

    /** 严格解析 access token（签名 + 过期校验），失败抛 {@code ApiException(UNAUTHORIZED)}。 */
    JwtPrincipal parseAccess(String accessToken);

    /** 严格解析 refresh token（轮换时使用，携带家族标识）。 */
    JwtPrincipal parseRefresh(String refreshToken);

    /** 宽容读取 access token 的 jti（登出黑名单用，容忍已过期）。 */
    String accessJti(String accessToken);

    Duration accessTtl();

    Duration refreshTtl();
}
