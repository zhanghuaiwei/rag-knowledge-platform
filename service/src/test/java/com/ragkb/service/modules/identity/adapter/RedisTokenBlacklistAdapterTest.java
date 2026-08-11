package com.ragkb.service.modules.identity.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistAdapterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisTokenBlacklistAdapter(redis);
    }

    @Test
    void blacklistSetsKeyWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        adapter.blacklist("jti-1", Duration.ofMinutes(10));
        verify(valueOps).set("auth:blk:jti-1", "1", Duration.ofMinutes(10));
    }

    @Test
    void nonPositiveTtlIsSkipped() {
        adapter.blacklist("jti-2", Duration.ofSeconds(-1));
        adapter.blacklist("jti-3", Duration.ZERO);
        verifyNoInteractions(redis);
    }

    @Test
    void isBlacklistedDelegatesToRedis() {
        when(redis.hasKey("auth:blk:jti-4")).thenReturn(true);
        assertTrue(adapter.isBlacklisted("jti-4"));

        when(redis.hasKey("auth:blk:jti-5")).thenReturn(false);
        assertFalse(adapter.isBlacklisted("jti-5"));
    }

    @Test
    void blankJtiSkipped() {
        adapter.blacklist("", Duration.ofMinutes(1));
        adapter.blacklist(null, Duration.ofMinutes(1));
        verifyNoInteractions(redis);
        verify(redis, never()).opsForValue();
    }
}
