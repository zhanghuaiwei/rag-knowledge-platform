package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.modules.identity.port.TokenBlacklistPort;
import com.ragkb.service.util.TodoSupport;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 黑名单适配器（TODO 桩，人工实现）。
 *
 * <p>人工实现点（StringRedisTemplate）：{@code SET auth:blk:{jti} "1" EX {ttlSeconds}} 与
 * {@code EXISTS auth:blk:{jti}}；key 前缀 {@code auth:blk:}，TTL = access 剩余有效期。
 */
@Component
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private final StringRedisTemplate redis;

    public RedisTokenBlacklistAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        TodoSupport.notImplemented("TokenBlacklistPort#blacklist");
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return TodoSupport.notImplemented("TokenBlacklistPort#isBlacklisted");
    }
}
