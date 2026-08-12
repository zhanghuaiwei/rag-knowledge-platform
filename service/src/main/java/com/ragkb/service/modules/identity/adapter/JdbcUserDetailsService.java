package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.config.IdentityConditions;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 表单登录的数据库 {@link UserDetailsService}（db.enabled=true 且 mode=form 时激活）。
 *
 * <p>职责：把 {@code user_credential} 加载为 Spring {@link UserDetails}，供
 * {@code DaoAuthenticationProvider} 用 {@code PasswordEncoder} 校验 BCrypt 密码，
 * 并实施"状态门禁"（DISABLED / 未过期 LOCKED → 拒绝）。**谁能在表单登录 = user_credential 有行
 * 且状态健康**，由数据库决定。
 *
 * <p>边界：
 * <ul>
 *   <li>本类不手写密码校验（BCrypt 匹配交给 DaoAuthenticationProvider + PasswordEncoder）；</li>
 *   <li>authorities 刻意留空——角色/租户由 {@code AuthServiceImpl} 经 {@code IdentityDirectory}
 *       每次从数据库重取，不信任登录时固化的角色；</li>
 *   <li>⚠️ 谨慎区（人工实现）：密码过期（{@code passwordExpiresAt}）强制轮换、失败计数的接入
 *       （认证失败 hook 调 {@code recordLoginFailure}）、锁定/解锁的完整时序。</li>
 * </ul>
 */
@Component
@Conditional(IdentityConditions.DbFormMode.class)
public class JdbcUserDetailsService implements UserDetailsService {

    private final UserCredentialStorePort credentialStore;

    public JdbcUserDetailsService(UserCredentialStorePort credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserCredentialStorePort.CredentialRecord credential = credentialStore.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        boolean locked = isLocked(credential);
        boolean disabled = "DISABLED".equals(credential.status()) || locked;
        return User.withUsername(credential.username())
                .password(credential.passwordHash())
                .disabled(disabled)
                // authorities 刻意留空：授权角色由 IdentityDirectory 登录时从数据库重取
                .authorities(java.util.List.of())
                .build();
    }

    /**
     * 锁定判定：status=LOCKED 且锁定截止时间未到 → 视为锁定；锁定已过期则放行（成功登录后由
     * {@code recordLoginSuccess} 重置状态）。
     *
     * <p>⚠️ 人工复核：该"自动解锁"语义需与 {@code recordLoginFailure} 的锁定/解锁时序保持一致。
     */
    private boolean isLocked(UserCredentialStorePort.CredentialRecord credential) {
        return "LOCKED".equals(credential.status())
                && credential.lockedUntil() != null
                && credential.lockedUntil().isAfter(Instant.now());
    }
}
