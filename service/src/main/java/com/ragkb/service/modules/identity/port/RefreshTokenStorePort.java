package com.ragkb.service.modules.identity.port;

import java.time.Duration;

/**
 * refresh token 族存储（轮换 + 复用检测）。
 *
 * <p>Redis key 约定：{@code auth:rf:{familyId}}，TTL = refresh 有效期。
 * 实现点由人工完成（CAS / 事务保证并发轮换安全，见模块契约）。
 */
public interface RefreshTokenStorePort {

    /** 首次签发 refresh token 时初始化家族（写入初始 jti）。 */
    void save(String familyId, String jti, Duration ttl);

    /**
     * 校验并轮换 refresh token：
     *
     * <ul>
     *   <li>{@code presentedJti} == 当前激活 jti → 写入 {@code newJti} 并返回 {@code true}（正常轮换）；</li>
     *   <li>否则视为被轮换掉的旧 token 被复用（疑似被盗）→ 吊销整族并返回 {@code false}。</li>
     * </ul>
     */
    boolean verifyAndRotate(String familyId, String presentedJti, String newJti, Duration ttl);

    /** 吊销整族会话（登出 / 复用检测命中）。 */
    void revoke(String familyId);
}
