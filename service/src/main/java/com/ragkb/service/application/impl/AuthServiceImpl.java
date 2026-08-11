package com.ragkb.service.application.impl;

import com.ragkb.service.application.AuthService;
import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKey;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreated;
import com.ragkb.service.interfaces.dto.AuthDtos.ApiKeyCreateRequest;
import com.ragkb.service.interfaces.dto.AuthDtos.AuthSession;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证会话用例桩实现（实现点由人工替换）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public String buildAuthorizeUrl(String returnTo) {
        return NotYetImplemented.stub("AuthService#buildAuthorizeUrl");
    }

    @Override
    public void handleCallback(String code, String state) {
        NotYetImplemented.stub("AuthService#handleCallback");
    }

    @Override
    public AuthSession session() {
        return NotYetImplemented.stub("AuthService#session");
    }

    @Override
    public void logout() {
        NotYetImplemented.stub("AuthService#logout");
    }

    @Override
    public AuthSession switchTenant(long tenantId) {
        return NotYetImplemented.stub("AuthService#switchTenant");
    }

    @Override
    public List<ApiKey> listApiKeys() {
        return NotYetImplemented.stub("AuthService#listApiKeys");
    }

    @Override
    public ApiKeyCreated createApiKey(ApiKeyCreateRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("AuthService#createApiKey");
    }

    @Override
    public void revokeApiKey(long keyId) {
        NotYetImplemented.stub("AuthService#revokeApiKey");
    }

    @Override
    public ApiKeyCreated rotateApiKey(long keyId, String idempotencyKey) {
        return NotYetImplemented.stub("AuthService#rotateApiKey");
    }
}
