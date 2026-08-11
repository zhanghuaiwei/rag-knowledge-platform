package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.modules.identity.port.RefreshTokenStorePort;
import com.ragkb.service.util.TodoSupport;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis refresh token 族存储适配器（TODO 桩，人工实现）。
 *
 * <p>人工实现点（StringRedisTemplate）：
 * <ul>
 *   <li>当前激活 jti 存 {@code auth:rf:{familyId}}（EX TTL=refresh 有效期）；</li>
 *   <li>{@code verifyAndRotate}：读取现值与 presentedJti 比较（CAS / Redis 事务保证并发安全），
 *       相等则 SETEX 写入 newJti 返回 true；不等则 DEL（吊销整族）返回 false；</li>
 *   <li>{@code revoke}：DEL {@code auth:rf:{familyId}}。</li>
 * </ul>
 */
@Component
public class RedisRefreshTokenStoreAdapter implements RefreshTokenStorePort {

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStoreAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean verifyAndRotate(String familyId, String presentedJti, String newJti, Duration ttl) {
        return TodoSupport.notImplemented("RefreshTokenStorePort#verifyAndRotate");
    }

    @Override
    public void revoke(String familyId) {
        TodoSupport.notImplemented("RefreshTokenStorePort#revoke");
    }
}
