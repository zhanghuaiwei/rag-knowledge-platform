package com.ragkb.service.modules.identity.service;

import com.ragkb.service.common.security.AuthenticatedPrincipal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * JWT token 生命周期用例。
 *
 * <p>access token 短命（默认 15m），前端仅内存持有；refresh token 长命（默认 30d），
 * 只存在于 HttpOnly cookie，轮换 + 复用检测（见
 * {@link com.ragkb.service.modules.identity.port.RefreshTokenStorePort}）。
 */
public interface TokenService {

    /** access/refresh 统一载荷视图；access token 无 {@code refreshFamilyId}。 */
    record JwtPrincipal(long userId, String subjectKey, String jti,
                        String refreshFamilyId, List<String> scopes,
                        List<String> tenantRoles, long tenantId)
            implements AuthenticatedPrincipal {

        @Override
        public long authenticatedUserId() {
            return userId;
        }
    }

    /** 一次签发的 access + refresh + 家族标识与 refresh jti（供 Store 初始化/校验）。 */
    record TokenPair(String accessToken, String refreshToken, String refreshFamilyId, String refreshJti,
                     Instant accessExpiresAt, Instant refreshExpiresAt) {
    }

    /**
     * 登录：签发新 refresh 家族的新 access + refresh。
     *
     * <p>access 短命并携带 scopes/tenantRoles/tenantId（签名凭证，权限仍按需二次校验）；
     * refresh 长命只携带 userId/subjectKey/tenantId + refreshFamilyId，不固化角色
     * （角色在每次轮换时由身份目录重新解析，避免长寿命 claim 成为永久权限真相）。
     */
    TokenPair issue(long userId, String subjectKey, List<String> scopes,
                    List<String> tenantRoles, long tenantId);

    /**
     * 轮换：同一 refresh 家族内签发新 access + 指定 jti 的新 refresh。
     * 调用方必须先经 {@code RefreshTokenStorePort#verifyAndRotate} 原子校验旧 jti，
     * 再以生成的新 refresh jti 调用本方法，保证 Store 现值与新 token 一致。
     */
    TokenPair issueRotated(long userId, String subjectKey, List<String> scopes,
                           List<String> tenantRoles, long tenantId, String familyId, String refreshJti);

    /** 严格解析 access token（签名 + 过期校验），失败抛 {@code ApiException(UNAUTHORIZED)}。 */
    JwtPrincipal parseAccess(String accessToken);

    /** 严格解析 refresh token（轮换时使用，携带家族标识）。 */
    JwtPrincipal parseRefresh(String refreshToken);

    /** 宽容读取 access token 的 jti（登出黑名单用，容忍已过期）；无法读取返回 null。 */
    String accessJti(String accessToken);

    Duration accessTtl();

    Duration refreshTtl();
}
