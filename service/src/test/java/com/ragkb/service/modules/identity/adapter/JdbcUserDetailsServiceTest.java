package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link JdbcUserDetailsService} 单测 —— mock {@link UserCredentialStorePort}，验证"状态门禁"：
 * 查不到即拒绝、DISABLED/LOCKED 未过期拒绝、锁定已过期放行。
 */
@ExtendWith(MockitoExtension.class)
class JdbcUserDetailsServiceTest {

    @Mock private UserCredentialStorePort credentialStore;

    private JdbcUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new JdbcUserDetailsService(credentialStore);
    }

    @Test
    void loadActiveUserReturnsUsernamePasswordAndEnabled() {
        when(credentialStore.findByUsername(anyString()))
                .thenReturn(Optional.of(record("ACTIVE", null)));

        UserDetails user = service.loadUserByUsername("admin");

        assertEquals("admin", user.getUsername());
        assertEquals("hash-abc", user.getPassword());
        assertTrue(user.isEnabled());
    }

    @Test
    void loadUnknownUserThrowsUsernameNotFound() {
        when(credentialStore.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nobody"));
    }

    @Test
    void loadDisabledUserIsDisabled() {
        when(credentialStore.findByUsername(anyString()))
                .thenReturn(Optional.of(record("DISABLED", null)));

        assertFalse(service.loadUserByUsername("admin").isEnabled());
    }

    @Test
    void loadLockedUserNotExpiredIsDisabled() {
        when(credentialStore.findByUsername(anyString()))
                .thenReturn(Optional.of(record("LOCKED", Instant.now().plusSeconds(600))));

        assertFalse(service.loadUserByUsername("admin").isEnabled());
    }

    @Test
    void loadLockedUserExpiredIsAllowed() {
        when(credentialStore.findByUsername(anyString()))
                .thenReturn(Optional.of(record("LOCKED", Instant.now().minusSeconds(60))));

        assertTrue(service.loadUserByUsername("admin").isEnabled());
    }

    // V0.5 契约：must_change_password / 密码过期不在此处拦截 —— 用户必须能登录后才能去改密
    // （拦截点在 CredentialPolicyGateFilter / 会话标志，而非 UserDetailsService）
    @Test
    void loadCredentialRequiringPasswordChangeStillAuthenticates() {
        when(credentialStore.findByUsername(anyString()))
                .thenReturn(Optional.of(new UserCredentialStorePort.CredentialRecord(
                        1L, 1L, "admin", "hash-abc", "ACTIVE", 0, null,
                        Instant.now(), null, true)));

        assertTrue(service.loadUserByUsername("admin").isEnabled());
    }

    @Test
    void loadCredentialWithExpiredPasswordStillAuthenticates() {
        when(credentialStore.findByUsername(anyString()))
                .thenReturn(Optional.of(new UserCredentialStorePort.CredentialRecord(
                        1L, 1L, "admin", "hash-abc", "ACTIVE", 0, null,
                        Instant.now(), Instant.now().minusSeconds(60), false)));

        assertTrue(service.loadUserByUsername("admin").isEnabled());
    }

    private UserCredentialStorePort.CredentialRecord record(String status, Instant lockedUntil) {
        return new UserCredentialStorePort.CredentialRecord(
                1L, 1L, "admin", "hash-abc", status, 0, lockedUntil,
                Instant.now(), null, false);
    }
}
