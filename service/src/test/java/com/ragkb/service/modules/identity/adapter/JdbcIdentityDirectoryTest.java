package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.modules.identity.persistence.entity.IdentityAccount;
import com.ragkb.service.modules.identity.persistence.entity.SysUser;
import com.ragkb.service.modules.identity.persistence.entity.UserCredential;
import com.ragkb.service.modules.identity.persistence.mapper.IdentityAccountMapper;
import com.ragkb.service.modules.identity.persistence.mapper.SysUserMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserCredentialMapper;
import com.ragkb.service.modules.identity.persistence.query.TenantMembershipRow;
import com.ragkb.service.modules.identity.port.IdentityDirectory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link JdbcIdentityDirectory} 单测 —— mock 各 Mapper，验证"谁能登录由数据库决定"的读取路径：
 * 查不到即拒绝、凭据/用户非 ACTIVE 即拒绝、成员关系组装（租户级联 + 角色聚合）。
 * 租户 ACTIVE 过滤在 SQL（TenantMemberMapper.xml）完成，单测以行集为输入验证聚合。
 */
@ExtendWith(MockitoExtension.class)
class JdbcIdentityDirectoryTest {

    @Mock private UserCredentialMapper userCredentialMapper;
    @Mock private IdentityAccountMapper identityAccountMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private TenantMemberMapper tenantMemberMapper;

    private JdbcIdentityDirectory directory;

    @BeforeEach
    void setUp() {
        // 初始化实体元数据，供 Lambda 包装器解析列名（单测无 Spring/MP 运行时）
        MpTableInfoSupport.init(UserCredential.class, IdentityAccount.class);
        directory = new JdbcIdentityDirectory(userCredentialMapper, identityAccountMapper,
                sysUserMapper, tenantMemberMapper);
    }

    // ---------- resolveBySubjectKey：form|username ----------

    @Test
    void resolveFormUserReturnsIdentityWhenCredentialAndUserActive() {
        when(userCredentialMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(activeCredential(9L, "admin"));
        when(sysUserMapper.selectById(9L)).thenReturn(activeUser("admin", "admin@ragkb.dev"));

        Optional<IdentityDirectory.ResolvedIdentity> identity =
                directory.resolveBySubjectKey("form|admin");

        assertTrue(identity.isPresent());
        assertEquals(9L, identity.get().userId());
        assertEquals("form|admin", identity.get().subjectKey());
        assertEquals("admin", identity.get().displayName());
        assertEquals("admin@ragkb.dev", identity.get().email());
    }

    @Test
    void resolveFormUserRejectsWhenCredentialMissing() {
        when(userCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertTrue(directory.resolveBySubjectKey("form|nobody").isEmpty());
    }

    @Test
    void resolveFormUserRejectsWhenCredentialNotActive() {
        UserCredential locked = activeCredential(9L, "admin");
        locked.setStatus("LOCKED");
        when(userCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(locked);

        assertTrue(directory.resolveBySubjectKey("form|admin").isEmpty());
    }

    @Test
    void resolveFormUserRejectsWhenSysUserNotActive() {
        when(userCredentialMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(activeCredential(9L, "admin"));
        SysUser disabled = activeUser("admin", "admin@ragkb.dev");
        disabled.setStatus("DISABLED");
        when(sysUserMapper.selectById(9L)).thenReturn(disabled);

        assertTrue(directory.resolveBySubjectKey("form|admin").isEmpty());
    }

    // ---------- resolveBySubjectKey：issuer|subject（OIDC 读取路径） ----------

    @Test
    void resolveOidcUserReturnsIdentityByIssuerAndSubject() {
        IdentityAccount account = new IdentityAccount();
        account.setUserId(9L);
        when(identityAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account);
        when(sysUserMapper.selectById(9L)).thenReturn(activeUser("张怀伟", "zhanghw@corp.example"));

        Optional<IdentityDirectory.ResolvedIdentity> identity =
                directory.resolveBySubjectKey("https://idp.example|sub-123");

        assertTrue(identity.isPresent());
        assertEquals(9L, identity.get().userId());
        assertEquals("https://idp.example|sub-123", identity.get().subjectKey());
        assertEquals("zhanghw@corp.example", identity.get().email());
    }

    @Test
    void resolveUnknownSubjectKeyRejects() {
        when(identityAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertTrue(directory.resolveBySubjectKey("https://idp.example|unknown").isEmpty());
    }

    @Test
    void resolveMalformedSubjectKeyRejects() {
        assertTrue(directory.resolveBySubjectKey("no-separator").isEmpty());
    }

    // ---------- memberships / membership（行集 → 租户聚合） ----------

    @Test
    void membershipsAggregatesRolesAndTenantMeta() {
        when(tenantMemberMapper.selectActiveMembershipRows(9L))
                .thenReturn(List.of(row(1L, "default", "默认租户", 3L, "TENANT_ADMIN"),
                        row(1L, "default", "默认租户", 3L, "KNOWLEDGE_ADMIN")));

        List<IdentityDirectory.TenantMembership> memberships = directory.memberships(9L);

        assertEquals(1, memberships.size());
        IdentityDirectory.TenantMembership m = memberships.get(0);
        assertEquals(1L, m.tenantId());
        assertEquals("default", m.tenantCode());
        assertEquals("默认租户", m.tenantName());
        assertEquals(List.of("TENANT_ADMIN", "KNOWLEDGE_ADMIN"), m.roles());
        assertEquals(3L, m.policyVersion());
    }

    @Test
    void membershipsMergesNoRoleIntoEmptyList() {
        when(tenantMemberMapper.selectActiveMembershipRows(9L))
                .thenReturn(List.of(row(1L, "default", "默认租户", 1L, null)));

        IdentityDirectory.TenantMembership m = directory.memberships(9L).get(0);

        assertTrue(m.roles().isEmpty());
    }

    @Test
    void membershipsReturnsEmptyWhenNoActiveRows() {
        when(tenantMemberMapper.selectActiveMembershipRows(9L)).thenReturn(List.of());

        assertTrue(directory.memberships(9L).isEmpty());
    }

    @Test
    void membershipReturnsMembershipForActiveRow() {
        when(tenantMemberMapper.selectMembershipRows(9L, 1L))
                .thenReturn(List.of(row(1L, "default", "默认租户", 2L, "TENANT_ADMIN")));

        Optional<IdentityDirectory.TenantMembership> membership = directory.membership(9L, 1L);

        assertTrue(membership.isPresent());
        assertEquals(2L, membership.get().policyVersion());
        assertEquals(List.of("TENANT_ADMIN"), membership.get().roles());
    }

    @Test
    void membershipReturnsEmptyWhenNoRow() {
        when(tenantMemberMapper.selectMembershipRows(eq(9L), eq(1L))).thenReturn(List.of());

        assertTrue(directory.membership(9L, 1L).isEmpty());
    }

    // ---------- 辅助 ----------

    private UserCredential activeCredential(long userId, String username) {
        UserCredential credential = new UserCredential();
        credential.setId(1L);
        credential.setUserId(userId);
        credential.setUsername(username);
        credential.setStatus("ACTIVE");
        return credential;
    }

    private SysUser activeUser(String displayName, String email) {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setDisplayName(displayName);
        user.setPrimaryEmail(email);
        user.setStatus("ACTIVE");
        return user;
    }

    private TenantMembershipRow row(long tenantId, String code, String name, long policyVersion, String role) {
        TenantMembershipRow row = new TenantMembershipRow();
        row.setTenantId(tenantId);
        row.setTenantCode(code);
        row.setTenantName(name);
        row.setPolicyVersion(policyVersion);
        row.setRole(role);
        return row;
    }
}
