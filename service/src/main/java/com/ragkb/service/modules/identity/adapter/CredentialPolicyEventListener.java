package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.config.IdentityConditions;
import com.ragkb.service.config.LocalAuthProperties;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 本地凭据策略接线点（仅 form + db 模式激活）：把认证失败/成功事件接到
 * {@link UserCredentialStorePort#recordLoginFailure/recordLoginSuccess}，实现失败锁定与计数重置。
 *
 * <p>⚠️ 谨慎区（人工实现）——本类仅提供事件入口与装配好的依赖，接线逻辑由人工实现：
 * <ul>
 *   <li>{@code onAuthenticationFailure}：从事件取登录标识（失败时 principal 可能是
 *       {@code UsernameNotFoundException} 等，需宽容取 username）→ {@code findByUsername} →
 *       命中则 {@code recordLoginFailure(id, localAuthProperties.maxFailedAttempts(),
 *       Duration.ofMinutes(lockoutMinutes()))}；查不到用户不记账（或统一 401 防枚举）。</li>
 *   <li>{@code onAuthenticationSuccess}：从事件取 {@code UserDetails.getUsername()} →
 *       {@code findByUsername} → {@code recordLoginSuccess(id, Instant.now())}；
 *       可一并更新 {@code sys_user.last_login_at}。</li>
 *   <li>锁定时序需与 {@code JdbcUserDetailsService} 的门禁判定（locked_until 比较）一致。</li>
 * </ul>
 */
@Component
@Conditional(IdentityConditions.DbFormMode.class)
public class CredentialPolicyEventListener {

    private final ObjectProvider<UserCredentialStorePort> credentialStoreProvider;
    private final LocalAuthProperties localAuthProperties;

    public CredentialPolicyEventListener(ObjectProvider<UserCredentialStorePort> credentialStoreProvider,
                                         LocalAuthProperties localAuthProperties) {
        this.credentialStoreProvider = credentialStoreProvider;
        this.localAuthProperties = localAuthProperties;
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        // ⚠️ 谨慎区（人工实现）：失败记账接线（见类注释契约）。
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        // ⚠️ 谨慎区（人工实现）：成功重置接线 + 更新 last_login_at（见类注释契约）。
    }
}
