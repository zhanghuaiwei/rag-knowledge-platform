package com.ragkb.service.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * 身份组件装配条件组合（{@code @ConditionalOnProperty} 不可重复，用 {@link AllNestedConditions} 表达 AND）。
 *
 * <p>见 {@code application.yml} 与 {@code deploy/ddl/migrations/V0.4__local_user_credentials.sql}：
 * 数据库启用后表单登录判定来自 {@code user_credential}/{@code sys_user}；无数据库（脚手架兜底）
 * 时降级为内存 dev 账号。
 */
public final class IdentityConditions {

    private IdentityConditions() {
    }

    /** form 模式 + 无数据库：内存 dev 账号兜底（LocalIdentityDirectory / devUserDetailsService）。 */
    public static class NoDbFormMode extends AllNestedConditions {

        public NoDbFormMode() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(name = "ragkb.auth.mode", havingValue = "form", matchIfMissing = true)
        static class FormMode {
        }

        @ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "false", matchIfMissing = true)
        static class DbDisabled {
        }
    }

    /** 数据库启用 + form 模式：本地账号登录从 user_credential 判定（JdbcUserDetailsService）。 */
    public static class DbFormMode extends AllNestedConditions {

        public DbFormMode() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
        static class DbEnabled {
        }

        @ConditionalOnProperty(name = "ragkb.auth.mode", havingValue = "form", matchIfMissing = true)
        static class FormMode {
        }
    }
}
