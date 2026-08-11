package com.ragkb.service.modules.identity.service;

import com.ragkb.service.modules.identity.vo.AuthSessionVo;
import com.ragkb.service.modules.identity.vo.ApiKeyVo;
import com.ragkb.service.modules.identity.vo.ApiKeyCreatedVo;
import com.ragkb.service.modules.identity.dto.ApiKeyCreateDto;
import com.ragkb.service.modules.identity.vo.TokenResponseVo;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.util.List;

/**
 * 认证与会话用例（实现点由人工完成；当前为接口契约）。
 *
 * <p>身份域遵循 03-详细设计：租户/用户由服务端从已验证身份推导，不信任客户端自报。
 * form 模式为 JWT：access token 进响应体（前端内存持有），refresh token 写 HttpOnly cookie。
 */
public interface AuthService {

    /** 登录/刷新内部结果：响应体 + 待写 HttpOnly cookie 的 refresh 凭证与有效期。 */
    record AuthResult(TokenResponseVo response, String refreshToken, Duration refreshCookieMaxAge) {
    }

    /** OIDC authorize 重定向地址（含 returnTo）。 */
    String buildAuthorizeUrl(String returnTo);

    /** OIDC callback 处理：交换 code 并建立 BFF 会话。 */
    void handleCallback(String code, String state);

    /** 当前会话概览；未登录抛 {@code ErrorCode.UNAUTHORIZED}。 */
    AuthSessionVo session();

    /** form 模式登录：认证通过后签发 access + refresh（refresh 由 Controller 写 cookie）。 */
    AuthResult login(Authentication authentication);

    /** 刷新：轮换 refresh token 并签发新 access；复用检测失败抛 {@code ErrorCode.UNAUTHORIZED}。 */
    AuthResult refresh(String rawRefreshToken);

    /** 登出：黑名单 access token 的 jti + 吊销 refresh 家族（幂等，容忍凭证缺失）。 */
    void logout(String accessToken, String rawRefreshToken);

    /** 切换激活租户。 */
    AuthSessionVo switchTenant(long tenantId);

    List<ApiKeyVo> listApiKeys();

    ApiKeyCreatedVo createApiKey(ApiKeyCreateDto request, String idempotencyKey);

    void revokeApiKey(long keyId);

    ApiKeyCreatedVo rotateApiKey(long keyId, String idempotencyKey);
}
