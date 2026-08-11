package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.modules.identity.port.RefreshTokenStorePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis refresh token 族存储（轮换 + 复用检测）。
 *
 * <p>key 约定 {@code auth:rf:{familyId}}，TTL = refresh 有效期。
 * 轮换用 Lua 脚本原子执行（CAS）：现值 == presentedJti 才写入新 jti；
 * 否则视为旧 refresh 被复用（疑似被盗）→ 立即删除整族。
 *
 * <p>⚠️ Redis 不可用时刷新/登出抛异常（不静默降级放行），与设计文档「Redis 故障默认拒绝」一致。
 */
@Component
public class RedisRefreshTokenStoreAdapter implements RefreshTokenStorePort {

    /** KEYS[1]=key，ARGV[1]=presentedJti，ARGV[2]=newJti，ARGV[3]=ttlSeconds。返回 1=正常轮换，0=复用吊销。 */
    private static final DefaultRedisScript<Long> VERIFY_AND_ROTATE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('SETEX', KEYS[1], ARGV[3], ARGV[2])
                return 1
            end
            redis.call('DEL', KEYS[1])
            return 0
            """, Long.class);

    private static final String KEY_PREFIX = "auth:rf:";

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStoreAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String familyId, String jti, Duration ttl) {
        String key = KEY_PREFIX + familyId;
        redis.opsForValue().set(key, jti, ttl);
    }

    @Override
    public boolean verifyAndRotate(String familyId, String presentedJti, String newJti, Duration ttl) {
        String key = KEY_PREFIX + familyId;
        Long result = redis.execute(VERIFY_AND_ROTATE,
                List.of(key), presentedJti, newJti, String.valueOf(ttl.getSeconds()));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void revoke(String familyId) {
        redis.delete(KEY_PREFIX + familyId);
    }
}
