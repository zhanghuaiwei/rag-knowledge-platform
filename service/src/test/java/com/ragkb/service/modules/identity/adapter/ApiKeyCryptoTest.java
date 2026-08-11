package com.ragkb.service.modules.identity.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyCryptoTest {

    private final ApiKeyCrypto crypto = new ApiKeyCrypto("test-pepper");

    @Test
    void secretHasPrefixAndHighEntropy() {
        String secret = crypto.generateSecret();
        assertTrue(secret.startsWith(ApiKeyCrypto.PLAIN_PREFIX));
        assertTrue(secret.length() > 32, "明文长度应 > 32（至少 256bit 熵的 base64url + 前缀）");
        assertNotEquals(secret, crypto.generateSecret());
    }

    @Test
    void digestIsDeterministicHexAndOneWay() {
        String raw = "rk_abcdefghijklmnopqrstuvwxyz123456";
        String digest = crypto.digest(raw);
        assertTrue(digest.matches("^[0-9a-f]{64}$"));
        assertEquals(digest, crypto.digest(raw));
        assertFalse(digest.contains(raw), "摘要不应包含明文");
    }

    @Test
    void digestDiffersAcrossSecrets() {
        String raw = "rk_same-input";
        String a = crypto.digest(raw);
        String b = new ApiKeyCrypto("other-pepper").digest(raw);
        assertNotEquals(a, b, "不同 pepper 摘要必须不同（防止换 pepper 后旧摘要误命中）");
    }

    @Test
    void prefixIsFirstTenChars() {
        String raw = "rk_abcdefghij";
        assertEquals("rk_abcdefg", crypto.prefix(raw));
    }

    @Test
    void looksLikeApiKeyDiscriminates() {
        assertTrue(crypto.looksLikeApiKey("rk_xxxx"));
        assertFalse(crypto.looksLikeApiKey("eyJhbGciOiJIUzI1NiJ9..."));
        assertFalse(crypto.looksLikeApiKey(null));
    }
}
