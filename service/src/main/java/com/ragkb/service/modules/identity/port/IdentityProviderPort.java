package com.ragkb.service.modules.identity.port;

import java.util.Optional;

/**
 * 身份提供方端口：解析/校验 OIDC 身份并映射全局用户（ADR-4）。
 * 平台不自建密码认证，只接受企业 IdP 验证后的 subject；
 * 角色不是资源授权的替代（05-技术选型 §3.4）。
 */
public interface IdentityProviderPort {

    Optional<String> resolveGlobalUserId(String issuer, String subject);

    boolean verifyAccessToken(String accessToken);
}
