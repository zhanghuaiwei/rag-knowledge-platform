package com.ragkb.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置（{@code ragkb.jwt.*}，见 application.yml）。
 *
 * <p>{@code secret} 生产必填（>=32 字节随机串，经 {@code RAGKB_JWT_SECRET} 注入）；
 * 用户在实现 {@code TokenServiceImpl} 时应校验其非空，防止生成弱密钥。
 */
@ConfigurationProperties(prefix = "ragkb.jwt")
public record JwtTokenProperties(
        String secret,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl,
        int refreshCookieMaxAgeSeconds) {
}
