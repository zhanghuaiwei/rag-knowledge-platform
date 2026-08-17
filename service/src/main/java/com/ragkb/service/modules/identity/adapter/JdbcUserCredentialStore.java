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
        // 单条原子 UPDATE 置新密码（改密/重置共用原语）：
        //   改密成功即恢复健康态 —— status=ACTIVE（解除锁定/禁用）、清失败计数与锁定时限，
        //   满足 DDL CHECK「status='LOCKED' 必须 locked_until 非空」的反向约束。
        LambdaUpdateWrapper<UserCredential> update = new LambdaUpdateWrapper<UserCredential>()
                .eq(UserCredential::getId, credentialId)                              // 按凭据主键精确定位
                .set(UserCredential::getPasswordHash, newPasswordHash)                // 只存 BCrypt 哈希，永不落明文
                .set(UserCredential::getPasswordChangedAt, now)                       // 记录本次改密时间（过期判定起点）
                .set(UserCredential::getMustChangePassword, mustChangePassword)       // 自助改密=false；管理员重置/建号=true
                .set(UserCredential::getStatus, "ACTIVE")                             // 改密成功视为凭据恢复可用
                .set(UserCredential::getFailedAttempts, 0)                            // 清历史失败计数
                .setSql("locked_until = NULL");                                       // 清锁定时限（配合 status 解除锁定）
        if (passwordExpiresAt != null) {
            // 启用密码过期策略：写入新过期时间点（now + expiryDays 由调用方计算）
            update.set(UserCredential::getPasswordExpiresAt, passwordExpiresAt);
        } else {
            // 未启用过期策略（expiryDays<=0）：显式写 NULL 覆盖历史残留，保证判定语义一致
            update.setSql("password_expires_at = NULL");
        }
        userCredentialMapper.update(null, update);
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
