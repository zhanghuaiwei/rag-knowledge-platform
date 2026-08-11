package com.ragkb.service.modules.identity.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreAdapterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisRefreshTokenStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisRefreshTokenStoreAdapter(redis);
    }

    @Test
    void saveSetsInitialJtiWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        adapter.save("family-1", "jti-a", Duration.ofDays(30));
        verify(valueOps).set("auth:rf:family-1", "jti-a", Duration.ofDays(30));
    }

    @Test
    void verifyAndRotateReturnsTrueWhenScriptSaysRotated() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);
        assertTrue(adapter.verifyAndRotate("family-1", "jti-old", "jti-new", Duration.ofDays(30)));
    }

    @Test
    void verifyAndRotateReturnsFalseWhenScriptSaysReused() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(0L);
        assertFalse(adapter.verifyAndRotate("family-1", "jti-stale", "jti-new", Duration.ofDays(30)));
    }

    @Test
    void revokeDeletesFamilyKey() {
        adapter.revoke("family-9");
        verify(redis).delete("auth:rf:family-9");
    }
}
