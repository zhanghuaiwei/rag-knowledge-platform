package com.ragkb.service.modules.identity.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫迁移种子 hash：{@code deploy/ddl/migrations/V0.4__local_user_credentials.sql} 内联的
 * bootstrap 管理员 BCrypt hash 必须可被 Spring {@link BCryptPasswordEncoder} 校验，且对应
 * 文档声明的引导口令 {@code admin123}（生产部署前必须重新生成并替换，见迁移文件头注释）。
 */
class BcryptSeedCompatibilityTest {

    /** 与 V0.4 迁移内联值保持一致；改动此常量必须同步改迁移 SQL 与引导口令说明。 */
    private static final String SEED_HASH = "$2y$10$mJmjunqPwc0cDJboJoL7cOHpum9CQdcL6/1bJhZpA8oZqFmg2LPd2";
    private static final String SEED_PASSWORD = "admin123";

    @Test
    void seedHashMatchesBootstrapPassword() {
        assertTrue(new BCryptPasswordEncoder().matches(SEED_PASSWORD, SEED_HASH),
                "V0.4 种子的 BCrypt hash 必须匹配引导口令 admin123");
    }
}
