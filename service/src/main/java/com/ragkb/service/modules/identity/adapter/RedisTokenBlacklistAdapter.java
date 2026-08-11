package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.modules.identity.port.TokenBlacklistPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis access token 黑名单（登出即时失效）。
 *
 * <p>key 约定 {@code auth:blk:{jti}}，TTL = access 剩余有效期（天然过期清理）。
 * 非正 TTL（token 已过期）不写入，避免残留脏键。
 */
@Component
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String KEY_PREFIX = "auth:blk:";

    private final StringRedisTemplate redis;

    public RedisTokenBlacklistAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
