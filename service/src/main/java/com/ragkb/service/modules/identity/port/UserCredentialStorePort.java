package com.ragkb.service.modules.identity.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 本地账号登录凭据存储端口（{@code user_credential} 表）。
 *
 * <p>职责：登录链路读写凭据——按登录标识加载（供 {@code UserDetailsService} 取 hash + 状态门禁）、
 * 失败/成功记账（失败锁定与计数重置，属凭据策略）。
 *
 * <p>边界：
 * <ul>
 *   <li>只操作凭据与凭据策略，不接触 {@code sys_user} 身份属性（display_name/email 等）；</li>
 *   <li>登录标识按 {@code lower(username)} 全局唯一（见 DDL 唯一索引）；</li>
 *   <li>密码永不落明文，仅存 BCrypt hash；校验由 Spring {@code PasswordEncoder} 完成，本端口不校验。</li>
 * </ul>
 *
 * <p>实现：
 * <ul>
 *   <li>{@code JdbcUserCredentialStore}（db.enabled=true）：MyBatis-Plus 读写；
 *       ⚠️ 失败计数策略（阈值锁定、lockout 时长）与成功重置为人工实现点（谨慎区）。</li>
 * </ul>
 */
public interface UserCredentialStorePort {

    /** 凭据记录（不含身份属性）。 */
    record CredentialRecord(long id, long userId, String username, String passwordHash,
                            String status, int failedAttempts, Instant lockedUntil,
                            Instant passwordChangedAt, Instant passwordExpiresAt,
                            boolean mustChangePassword) {
    }

    /**
     * 按登录标识精确匹配加载凭据；未找到返回空（del_flag=0 由 {@code @TableLogic} 自动过滤）。
     * 唯一索引 {@code lower(username)} 保证至多命中一行。
     */
    Optional<CredentialRecord> findByUsername(String username);

    /**
     * 按全局用户 id 加载凭据（同一用户至多一条本地账号凭据）；供自助改密、会话策略门禁等
     * 以当前登录用户维度重读凭据。
     */
    Optional<CredentialRecord> findByUserId(long userId);

    /**
     * 置新密码（改密/重置共用原语）：更新 {@code password_hash}、{@code password_changed_at}、
     * {@code password_expires_at}、{@code must_change_password}，并清失败计数/锁定，状态置 {@code ACTIVE}。
     *
     * <p>⚠️ 谨慎区（人工实现）：必须用单条 {@code LambdaUpdateWrapper} 原子 SET；
     * {@code mustChangePassword} 由调用方决定（自助改密置 false，管理员重置/建号置 true）。
     */
    void updatePassword(long credentialId, String newPasswordHash, Instant now,
                        Instant passwordExpiresAt, boolean mustChangePassword);

    /**
     * 登录失败记账：原子递增失败计数，达阈值置 {@code status=LOCKED} + {@code lockedUntil}。
     *
     * <p>⚠️ 人工实现点（谨慎区）：必须用单条原子 {@code UPDATE ... SET failed_attempts = failed_attempts + 1}
     * （禁止读改写，防并发登录竞争覆盖）；{@code lockedUntil} 建议用数据库时间（now()）避免应用时钟漂移。
     */
    void recordLoginFailure(long credentialId, int maxFailedAttempts, Duration lockoutDuration);

    /**
     * 登录成功记账：重置失败计数与锁定状态；可选的刷新 {@code sys_user.last_login_at}。
     *
     * <p>⚠️ 人工实现点（谨慎区）：失败计数/锁定时序需与 {@code JdbcUserDetailsService} 的门禁判定一致。
     */
    void recordLoginSuccess(long credentialId, Instant now);
}
