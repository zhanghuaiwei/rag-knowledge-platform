package com.ragkb.service.modules.identity.port;

import java.time.Duration;

/**
 * access token 黑名单（登出即时失效）。
 *
 * <p>Redis key 约定：{@code auth:blk:{jti}}，TTL = access 剩余有效期（天然过期清理）。
 * 实现点由人工完成（Redis 命令见模块契约）。
 */
public interface TokenBlacklistPort {

    /** 将 access token 的 jti 加入黑名单。 */
    void blacklist(String jti, Duration ttl);

    /** jti 是否已被吊销。 */
    boolean isBlacklisted(String jti);
}
