package com.ragkb.service.modules.identity.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragkb.service.modules.identity.persistence.entity.UserCredential;
import com.ragkb.service.modules.identity.persistence.mapper.UserCredentialMapper;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * {@code user_credential} 持久化实现（db.enabled=true 时激活）。
 *
 * <p>登录标识按精确匹配加载；失败计数用单条原子 {@code UPDATE ... SET failed_attempts = failed_attempts + 1}
 * （不做读改写，防并发登录竞争覆盖）；锁定时限用数据库时间（now()）避免应用时钟漂移。
 *
 * <p>⚠️ 谨慎区（人工复核）：凭据策略（阈值语义、锁定后解锁时机、密码过期强制轮换）与本类
 * 被谁调用的接线（认证失败/成功 hook）为人工实现点，本类只提供正确且可测的原子机制骨架。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class JdbcUserCredentialStore implements UserCredentialStorePort {

    private final UserCredentialMapper userCredentialMapper;

    public JdbcUserCredentialStore(UserCredentialMapper userCredentialMapper) {
        this.userCredentialMapper = userCredentialMapper;
    }

    /**
     * 认证的UserService对象,去查db
     */
    @Override
    public Optional<CredentialRecord> findByUsername(String username) {
        // 唯一索引 lower(username) 保证大小写不敏感唯一；按输入精确匹配加载
        UserCredential credential = userCredentialMapper.selectOne(
                new LambdaQueryWrapper<UserCredential>().eq(UserCredential::getUsername, username));
        return Optional.ofNullable(credential).map(this::toRecord);
    }

    @Override
    public Optional<CredentialRecord> findByUserId(long userId) {
        // 同一全局用户至多一条本地凭据（user_id 索引 + 业务不变量）
        UserCredential credential = userCredentialMapper.selectOne(
                new LambdaQueryWrapper<UserCredential>().eq(UserCredential::getUserId, userId));
        return Optional.ofNullable(credential).map(this::toRecord);
    }

    @Override
    @Transactional
    public void updatePassword(long credentialId, String newPasswordHash, Instant now,
                               Instant passwordExpiresAt, boolean mustChangePassword) {
        // ⚠️ 谨慎区（人工实现）：单条原子 UPDATE ——
        //   SET password_hash = #{newPasswordHash}
        //     , password_changed_at = #{now}
        //     , password_expires_at = #{passwordExpiresAt}
        //     , must_change_password = #{mustChangePassword}
        //     , status = 'ACTIVE'               -- 重置/改密后解除锁定与禁用
        //     , failed_attempts = 0
        //     , locked_until = NULL
        //   WHERE id = #{credentialId}
        throw new UnsupportedOperationException("TODO: 人工实现 JdbcUserCredentialStore#updatePassword");
    }

    @Override
    @Transactional
    public void recordLoginFailure(long credentialId, int maxFailedAttempts, Duration lockoutDuration) {
        // 1) 原子递增失败计数（禁止读改写；并发登录各自 +1 不互相覆盖）
        userCredentialMapper.update(null, new LambdaUpdateWrapper<UserCredential>()
                .eq(UserCredential::getId, credentialId)
                .setSql("failed_attempts = failed_attempts + 1"));
        // 2) 达到阈值且仍 ACTIVE 才锁定（第二个 UPDATE 读到的是递增后的值）；
        //    locked_until 用 DB 时间 now()，锁定时长来自策略配置（非用户输入，注入面为整数秒）
        long lockoutSeconds = lockoutDuration.toSeconds();
        userCredentialMapper.update(null, new LambdaUpdateWrapper<UserCredential>()
                .eq(UserCredential::getId, credentialId)
                .eq(UserCredential::getStatus, "ACTIVE")
                .ge(UserCredential::getFailedAttempts, maxFailedAttempts)
                .set(UserCredential::getStatus, "LOCKED")
                .setSql("locked_until = now() + make_interval(secs => " + lockoutSeconds + ")"));
    }

    @Override
    @Transactional
    public void recordLoginSuccess(long credentialId, Instant now) {
        // 重置为健康态：清失败计数、清锁定时限、恢复 ACTIVE（满足 status='LOCKED' 需 locked_until 非空 的 CHECK）
        userCredentialMapper.update(null, new LambdaUpdateWrapper<UserCredential>()
                .eq(UserCredential::getId, credentialId)
                .set(UserCredential::getStatus, "ACTIVE")
                .set(UserCredential::getFailedAttempts, 0)
                .setSql("locked_until = NULL"));
    }

    private CredentialRecord toRecord(UserCredential entity) {
        return new CredentialRecord(
                entity.getId(), entity.getUserId(), entity.getUsername(), entity.getPasswordHash(),
                entity.getStatus(), entity.getFailedAttempts() != null ? entity.getFailedAttempts() : 0,
                entity.getLockedUntil(), entity.getPasswordChangedAt(), entity.getPasswordExpiresAt(),
                Boolean.TRUE.equals(entity.getMustChangePassword()));
    }
}
