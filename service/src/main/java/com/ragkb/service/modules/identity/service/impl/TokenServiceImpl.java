package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.util.TodoSupport;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * JWT 签发/校验用例（TODO 桩，人工实现）。
 *
 * <p>人工实现点（JJWT）：
 * <ul>
 *   <li>HS256 签名，密钥来自 {@code RAGKB_JWT_SECRET}（启动时校验非空，>=32 字节防弱密钥）；</li>
 *   <li>iss/{@code RAGKB_JWT_ISSUER} / aud / exp / iat / jti 声明校验（access 与 refresh 各一套）；</li>
 *   <li>access 载荷：userId、subjectKey、jti、scopes、tenantId；refresh 载荷：userId、jti、refreshFamilyId。</li>
 * </ul>
 */
@Service
public class TokenServiceImpl implements TokenService {

    @Override
    public TokenPair issue(long userId, String subjectKey, List<String> scopes, long tenantId) {
        return TodoSupport.notImplemented("TokenService#issue");
    }

    @Override
    public JwtPrincipal parseAccess(String accessToken) {
        return TodoSupport.notImplemented("TokenService#parseAccess");
    }

    @Override
    public JwtPrincipal parseRefresh(String refreshToken) {
        return TodoSupport.notImplemented("TokenService#parseRefresh");
    }

    @Override
    public String accessJti(String accessToken) {
        return TodoSupport.notImplemented("TokenService#accessJti");
    }

    @Override
    public Duration accessTtl() {
        return TodoSupport.notImplemented("TokenService#accessTtl");
    }

    @Override
    public Duration refreshTtl() {
        return TodoSupport.notImplemented("TokenService#refreshTtl");
    }
}
