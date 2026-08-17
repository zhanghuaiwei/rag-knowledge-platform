package com.ragkb.service.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.config.LocalAuthProperties;
import com.ragkb.service.modules.identity.adapter.MpTableInfoSupport;
import com.ragkb.service.modules.identity.dto.CreateLocalUserRequest;
import com.ragkb.service.modules.identity.persistence.entity.TenantMember;
import com.ragkb.service.modules.identity.persistence.mapper.SysUserMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberMapper;
import com.ragkb.service.modules.identity.persistence.mapper.TenantMemberRoleMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserAccountMapper;
import com.ragkb.service.modules.identity.persistence.mapper.UserCredentialMapper;
import com.ragkb.service.modules.identity.persistence.query.UserAccountRow;
import com.ragkb.service.modules.identity.persistence.query.UserOrgRow;
import com.ragkb.service.modules.identity.persistence.query.UserRoleRow;
import com.ragkb.service.modules.identity.port.UserCredentialStorePort;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.identity.service.UserAccountService;
import com.ragkb.service.modules.identity.vo.UserVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserAccountServiceImpl} 单测 —— AI 实现部分（listUsers 聚合/状态映射、
 * disable/enable 的租户范围 SQL）与谨慎区骨架（TodoSupport 抛错占位，人工实现后换行为断言）。
 */
@ExtendWith(MockitoExtension.class)
class UserAccountServiceImplTest {

    @Mock private UserAccountMapper userAccountMapper;
    @Mock private TenantMemberMapper tenantMemberMapper;
    @Mock private TenantMemberRoleMapper tenantMemberRoleMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private UserCredentialMapper userCredentialMapper;
    @Mock private UserCredentialStorePort credentialStore;
    @Mock private ObjectProvider<PasswordEncoder> passwordEncoderProvider;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        MpTableInfoSupport.init(TenantMember.class);
        service = new UserAccountServiceImpl(
                userAccountMapper, tenantMemberMapper, tenantMemberRoleMapper,
                sysUserMapper, userCredentialMapper, credentialStore,
                passwordEncoderProvider, new LocalAuthProperties(5, 15, 180));
        setJwtContext(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setJwtContext(long tenantId) {
        var principal = new TokenService.JwtPrincipal(
                1L, "form|admin", "jti", null, List.of("web"), List.of("TENANT_ADMIN"), tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "t", List.of()));
    }

    private UserAccountRow row(long userId, String name, String email, String memberStatus) {
        UserAccountRow r = new UserAccountRow();
        r.setUserId(userId);
        r.setDisplayName(name);
        r.setPrimaryEmail(email);
        r.setMemberStatus(memberStatus);
        r.setMustChangePassword(true);
        r.setLastLoginAt(Instant.parse("2026-08-01T00:00:00Z"));
        return r;
    }

    // ---------- listUsers ----------

    @Test
    void listUsersAggregatesRolesAndMapsSuspendedToDisabled() {
        List<UserAccountRow> rows = List.of(
                row(1L, "张三", "zhang@example.com", "ACTIVE"),
                row(2L, "李四", "li@example.com", "SUSPENDED"));
        when(userAccountMapper.selectMemberPage(eq(1L), eq(0L), eq(20))).thenReturn(rows);
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList())).thenReturn(List.of(
                roleRow(1L, "TENANT_ADMIN"), roleRow(1L, "MEMBER"), roleRow(2L, "MEMBER")));
        when(userAccountMapper.selectMemberOrgs(eq(1L), anyList())).thenReturn(List.of(
                orgRow(1L, "研发中心")));
        when(userAccountMapper.countMembers(1L)).thenReturn(2L);

        PageData<UserVo> page = service.listUsers(1, 20);

        assertEquals(2, page.total());
        assertEquals(List.of("TENANT_ADMIN", "MEMBER"), page.items().get(0).roles(),
                "多角色行须聚合为 roles 列表");
        assertEquals("研发中心", page.items().get(0).orgName());
        assertEquals("DISABLED", page.items().get(1).status(), "SUSPENDED 成员映射为 DISABLED 展示");
        assertTrue(page.items().get(0).mustChangePassword(), "mustChangePassword 从查询行透传");
    }

    // ---------- disable / enable ----------

    @Test
    void disableUserFlipsTenantMemberStatusScopedByTenant() {
        when(tenantMemberMapper.update(isNull(), any())).thenReturn(1);
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "王五", "wang@example.com", "SUSPENDED")));
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList())).thenReturn(List.of(roleRow(9L, "MEMBER")));
        when(userAccountMapper.selectMemberOrgs(eq(1L), anyList())).thenReturn(List.of());

        UserVo vo = service.disableUser(9L);

        assertEquals("DISABLED", vo.status());
        ArgumentCaptor<LambdaUpdateWrapper<TenantMember>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tenantMemberMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        String segment = captor.getValue().getSqlSegment();
        assertTrue(sqlSet.contains("status"), "禁用须置 status");
        assertTrue(segment.contains("tenant_id"), "必须按当前租户限定");
        assertTrue(segment.contains("user_id"), "必须按用户限定");
        assertTrue(sqlSet.contains("suspended_at"), "禁用须记录 suspended_at");
    }

    @Test
    void enableUserClearsSuspendedAt() {
        when(tenantMemberMapper.update(isNull(), any())).thenReturn(1);
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "王五", "wang@example.com", "ACTIVE")));
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList())).thenReturn(List.of(roleRow(9L, "MEMBER")));
        when(userAccountMapper.selectMemberOrgs(eq(1L), anyList())).thenReturn(List.of());

        service.enableUser(9L);

        ArgumentCaptor<LambdaUpdateWrapper<TenantMember>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tenantMemberMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("suspended_at = NULL"),
                "启用须清空 suspended_at");
        assertTrue(captor.getValue().getSqlSegment().contains("tenant_id"),
                "必须按当前租户限定");
    }

    @Test
    void disableUnknownUserThrowsNotFound() {
        when(tenantMemberMapper.update(isNull(), any())).thenReturn(0);

        assertThrows(com.ragkb.service.common.exception.ApiException.class, () -> service.disableUser(99L));
    }

    // ---------- 谨慎区骨架（人工实现后替换为行为断言） ----------

    @Test
    void createLocalUserIsManualSkeleton() {
        assertThrows(UnsupportedOperationException.class, () -> service.createLocalUser(
                new CreateLocalUserRequest("zhangsan", "zhang@example.com", "张三", "secret1", List.of("MEMBER"))));
    }

    @Test
    void setRolesIsManualSkeleton() {
        assertThrows(UnsupportedOperationException.class, () -> service.setRoles(9L, List.of("MEMBER")));
    }

    @Test
    void removeFromTenantIsManualSkeleton() {
        assertThrows(UnsupportedOperationException.class, () -> service.removeFromTenant(9L));
    }

    @Test
    void resetPasswordIsManualSkeleton() {
        assertThrows(UnsupportedOperationException.class, () -> service.resetPassword(9L, "newpass1"));
    }

    @Test
    void updateUserOrgIsManualSkeleton() {
        assertThrows(UnsupportedOperationException.class, () -> service.updateUserOrg(9L, 1L));
    }

    private UserRoleRow roleRow(long userId, String role) {
        UserRoleRow r = new UserRoleRow();
        r.setUserId(userId);
        r.setRole(role);
        return r;
    }

    private UserOrgRow orgRow(long userId, String orgName) {
        UserOrgRow r = new UserOrgRow();
        r.setUserId(userId);
        r.setOrgName(orgName);
        return r;
    }
}
