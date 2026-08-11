package com.ragkb.service.application;

import com.ragkb.service.interfaces.dto.AuthDtos.AuthSession;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKey;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreated;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreateRequest;

import java.util.List;

/**
 * 认证与会话用例（实现点由人工完成；当前为接口契约）。
 *
 * <p>身份域遵循 03-详细设计：租户/用户由服务端从已验证身份推导，不信任客户端自报。
 */
public interface AuthService {

    /** OIDC authorize 重定向地址（含 returnTo）。 */
    String buildAuthorizeUrl(String returnTo);

    /** OIDC callback 处理：交换 code 并建立 BFF 会话。 */
    void handleCallback(String code, String state);

    /** 当前会话概览；未登录抛 {@code ErrorCode.UNAUTHORIZED}。 */
    AuthSession session();

    void logout();

    /** 切换激活租户。 */
    AuthSession switchTenant(long tenantId);

    List<ApiKey> listApiKeys();

    ApiKeyCreated createApiKey(ApiKeyCreateRequest request, String idempotencyKey);

    void revokeApiKey(long keyId);

    ApiKeyCreated rotateApiKey(long keyId, String idempotencyKey);
}
