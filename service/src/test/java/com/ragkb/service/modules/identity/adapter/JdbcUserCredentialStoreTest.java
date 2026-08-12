package com.ragkb.service.modules.identity.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragkb.service.modules.identity.persistence.entity.UserCredential;
import com.ragkb.service.modules.identity.persistence.mapper.UserCredentialMapper;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JdbcUserCredentialStore} 单测 —— 契约级：登录标识行映射、失败记账原子性（无读改写）、
 * 阈值锁定与成功重置的 SQL 形状（update 语句原子、条件含 id）。
 */
@ExtendWith(MockitoExtension.class)
class JdbcUserCredentialStoreTest {

    @Mock private UserCredentialMapper userCredentialMapper;

    private JdbcUserCredentialStore store;

    @BeforeEach
    void setUp() {
        // 初始化实体元数据，供 Lambda 包装器解析列名（单测无 Spring/MP 运行时）
        MpTableInfoSupport.init(UserCredential.class);
        store = new JdbcUserCredentialStore(userCredentialMapper);
    }

    @Test
    void findByUsernameMapsRowToRecord() {
        UserCredential entity = new UserCredential();
        entity.setId(1L);
        entity.setUserId(9L);
        entity.setUsername("admin");
        entity.setPasswordHash("$2y$10$hash");
        entity.setStatus("ACTIVE");
        entity.setFailedAttempts(2);
        entity.setLockedUntil(null);
        entity.setPasswordChangedAt(Instant.parse("2026-01-01T00:00:00Z"));
        entity.setPasswordExpiresAt(null);
        entity.setMustChangePassword(true);
        when(userCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        Optional<UserCredentialStorePort.CredentialRecord> record = store.findByUsername("admin");

        assertTrue(record.isPresent());
        assertEquals(1L, record.get().id());
        assertEquals(9L, record.get().userId());
        assertEquals("admin", record.get().username());
        assertEquals("$2y$10$hash", record.get().passwordHash());
        assertEquals("ACTIVE", record.get().status());
        assertEquals(2, record.get().failedAttempts());
        assertEquals(entity.getPasswordChangedAt(), record.get().passwordChangedAt());
        assertTrue(record.get().mustChangePassword(), "must_change_password 必须映射到 CredentialRecord");
    }

    @Test
    void findByUsernameReturnsEmptyWhenMissing() {
        when(userCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertTrue(store.findByUsername("nobody").isEmpty());
    }

    @Test
    void findByUserIdMapsRowToRecord() {
        UserCredential entity = new UserCredential();
        entity.setId(3L);
        entity.setUserId(9L);
        entity.setUsername("zhangsan");
        entity.setPasswordHash("$2y$10$hash");
        entity.setStatus("ACTIVE");
        entity.setFailedAttempts(0);
        entity.setMustChangePassword(true);
        when(userCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        Optional<UserCredentialStorePort.CredentialRecord> record = store.findByUserId(9L);

        assertTrue(record.isPresent());
        assertEquals(3L, record.get().id());
        assertEquals(9L, record.get().userId());
        assertTrue(record.get().mustChangePassword());
    }

    @Test
    void updatePasswordIsManualSkeletonUntilHumanImplements() {
        // ⚠️ 谨慎区人工实现点：骨架当前抛 UnsupportedOperationException；人工实现后替换为行为断言
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> store.updatePassword(7L, "$2y$10$new", Instant.now(), null, true));
    }

    @Test
    void recordLoginFailureIncrementsAtomicallyThenLocksAtThreshold() {
        when(userCredentialMapper.update(isNull(), any())).thenReturn(1);

        store.recordLoginFailure(7L, 5, Duration.ofMinutes(15));

        ArgumentCaptor<LambdaUpdateWrapper<UserCredential>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userCredentialMapper, times(2)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<UserCredential>> wrappers = captor.getAllValues();

        // 原子递增（单条 UPDATE，条件含 id；全程无 selectOne ⇒ 非读改写，防并发覆盖）
        assertTrue(wrappers.get(0).getSqlSet().contains("failed_attempts = failed_attempts + 1"),
                "递增必须原子 UPDATE（failed_attempts = failed_attempts + 1）");
        assertTrue(wrappers.get(0).getSqlSegment().contains("id"),
                "递增 UPDATE 必须按凭据 id 定位");
        // 条件锁定（第二个 UPDATE 读到递增后值；锁定时长用 DB 时间 now()；阈值条件在 WHERE）
        assertTrue(wrappers.get(1).getSqlSet().contains("status"),
                "锁定 UPDATE 必须置 status");
        assertTrue(wrappers.get(1).getSqlSet().contains("make_interval(secs => 900)"),
                "锁定时长必须用数据库时间 now() + interval");
        String lockSegment = wrappers.get(1).getSqlSegment();
        assertTrue(lockSegment.contains("status") && lockSegment.contains("failed_attempts"),
                "锁定 UPDATE 必须仅在未锁定且达阈值时生效（status/failed_attempts 条件）");
        verify(userCredentialMapper, never()).selectOne(any());
    }

    @Test
    void recordLoginSuccessResetsToHealthyState() {
        when(userCredentialMapper.update(isNull(), any())).thenReturn(1);

        store.recordLoginSuccess(7L, Instant.now());

        ArgumentCaptor<LambdaUpdateWrapper<UserCredential>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userCredentialMapper, times(1)).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("status"));
        assertTrue(sqlSet.contains("failed_attempts"));
        assertTrue(sqlSet.contains("locked_until = NULL"));
    }
}
