package com.ragkb.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地账号凭据策略配置（{@code ragkb.auth.local.*}，见 application.yml）。
 *
 * <p>供表单登录（form 模式 + 数据库）的凭据策略实现使用：失败锁定阈值/锁定时长/密码过期天数。
 * 默认值仅 dev 兜底，生产通过 {@code RAGKB_LOCAL_*} 注入。⚠️ 凭据策略接线（认证失败/成功 hook）
 * 为人工实现点，本类只提供配置源。
 */
@ConfigurationProperties(prefix = "ragkb.auth.local")
public record LocalAuthProperties(
        /** 连续失败锁定阈值（次）；&lt;=0 表示不启用失败锁定。 */
        int maxFailedAttempts,
        /** 锁定持续时长（分钟）。 */
        int lockoutMinutes,
        /** 密码过期天数（天）；&lt;=0 表示不启用密码过期强制轮换。 */
        int passwordExpiryDays) {
}
