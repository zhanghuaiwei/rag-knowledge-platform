package com.ragkb.service.modules.identity.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * API Key 明文生成与摘要（认证授权 §4.3）。
 *
 * <ul>
 *   <li>明文：{@code rk_} + 32 字节 CSPRNG 的 base64url（≥256bit 熵），只在创建/轮换时展示一次；</li>
 *   <li>摘要：{@code SHA-256(serverPepper + raw)} 的 hex（64 位），是唯一入库形式，明文与摘要不进日志；</li>
 *   <li>prefix：明文前 10 字符，用于请求时快速定位候选行。</li>
 * </ul>
 *
 * <p>pepper 来自 {@code RAGKB_API_KEY_PEPPER}（生产必填；默认值为 dev 兜底，仅限 form/dev 场景）。
 */
@Component
public class ApiKeyCrypto {

    /** 明文固定前缀，认证链用它区分 JWT 与 API Key（不能把 API Key 误交给 JWT Parser）。 */
    public static final String PLAIN_PREFIX = "rk_";

    /** 认证请求中用于查库的候选定位串 = prefix + digest（filter 用）。 */
    private static final String PEPPER_DEFAULT = "dev-only-api-key-pepper-change-me";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final String pepper;

    public ApiKeyCrypto(@Value("${ragkb.api-key.pepper:}") String pepper) {
        this.pepper = pepper != null && !pepper.isBlank() ? pepper : PEPPER_DEFAULT;
    }

    /** 生成新明文（>=256bit 熵）。 */
    public String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return PLAIN_PREFIX + BASE64_URL.encodeToString(bytes);
    }

    /** 服务端摘要（带 pepper 的 SHA-256 hex，入库唯一形式）。 */
    public String digest(String rawSecret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((pepper + rawSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 明文识别前缀（请求认证用，存库用于索引）。 */
    public String prefix(String rawSecret) {
        return rawSecret.substring(0, Math.min(10, rawSecret.length()));
    }

    /** 是否为平台签发的 API Key 明文（Bearer 分流判断）。 */
    public boolean looksLikeApiKey(String bearerToken) {
        return bearerToken != null && bearerToken.startsWith(PLAIN_PREFIX);
    }
}
