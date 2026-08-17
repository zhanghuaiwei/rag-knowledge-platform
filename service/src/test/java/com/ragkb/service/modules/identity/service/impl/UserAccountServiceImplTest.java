package com.ragkb.service.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.config.LocalAuthProperties;
import com.ragkb.service.modules.identity.adapter.MpTableInfoSupport;
import com.ragkb.service.modules.identity.dto.CreateLocalUserRequest;
import com.ragkb.service.modules.identity.persistence.entity.SysUser;
import com.ragkb.service.modules.identity.persistence.entity.TenantMember;
import com.ragkb.service.modules.identity.persistence.entity.TenantMemberRole;
import com.ragkb.service.modules.identity.persistence.entity.UserCredential;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link UserAccountServiceImpl} 单测 —— 查询聚合（listUsers）、状态翻转 SQL（disable/enable）
 * 与安全敏感写操作的行为断言（建号四表事务/角色替换守卫/移出租户守卫/重置密码策略/组织调整校验）。
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
    @Mock private PasswordEncoder passwordEncoder;

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

    // ---------- createLocalUser（建号四表事务 + 首登强制改密 + 用户名冲突） ----------

    @Test
    void createLocalUserInsertsFourTablesAndForcesFirstLoginChange() {
        // form 模式装配 BCrypt 编码器；初始密码按统一强度策略校验后编码
        when(passwordEncoderProvider.getIfAvailable()).thenReturn(passwordEncoder);
        when(passwordEncoder.encode("initpass1")).thenReturn("$2y$10$hash");
        // sys_user 插入后回填自增主键（供凭据/成员/角色行外键引用）
        doAnswer(invocation -> {
            ((SysUser) invocation.getArgument(0)).setId(66L);
            return 1;
        }).when(sysUserMapper).insert(any(SysUser.class));
        // 建号成功后 loadUserVo 重读成员视图（单行 + 角色 + 无组织）
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(66L, "张三", "zhang@example.com", "ACTIVE")));
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList()))
                .thenReturn(List.of(roleRow(66L, "MEMBER")));
        when(userAccountMapper.selectMemberOrgs(eq(1L), anyList())).thenReturn(List.of());
        when(userAccountMapper.insertAuditLog(eq(1L), eq(1L), anyString(), anyString())).thenReturn(1);

        UserVo vo = service.createLocalUser(new CreateLocalUserRequest(
                "zhangsan", "zhang@example.com", "张三", "initpass1", List.of("MEMBER")));

        assertEquals(66L, vo.id());
        // 凭据行：BCrypt 哈希 + 首登强制改密 + 过期时间按策略计算（expiryDays=180 → 非 null）
        ArgumentCaptor<UserCredential> credentialCaptor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialMapper).insert(credentialCaptor.capture());
        assertEquals("$2y$10$hash", credentialCaptor.getValue().getPasswordHash());
        assertTrue(credentialCaptor.getValue().getMustChangePassword(), "建号必须置首登强制改密");
        assertEquals(66L, credentialCaptor.getValue().getUserId());
        assertTrue(credentialCaptor.getValue().getPasswordExpiresAt() != null
                        && credentialCaptor.getValue().getPasswordExpiresAt().isAfter(Instant.now()),
                "启用过期策略时必须写入未来过期时间");
        // 成员关系与角色行各一
        verify(tenantMemberMapper).insert(any(TenantMember.class));
        verify(tenantMemberRoleMapper).insert(any(TenantMemberRole.class));
        // 审计：动作与对象 id（不含密码）
        verify(userAccountMapper).insertAuditLog(1L, 1L, "tenant_member.create_local", "66");
    }

    @Test
    void createLocalUserRejectsWeakInitialPassword() {
        // 7 位无数字：不满足统一强度策略，事务未开始（无任何表写入）
        ApiException ex = assertThrows(ApiException.class, () -> service.createLocalUser(
                new CreateLocalUserRequest("zhangsan", "zhang@example.com", "张三", "short1", List.of("MEMBER"))));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verifyNoInteractions(sysUserMapper, userCredentialMapper, tenantMemberMapper, tenantMemberRoleMapper);
    }

    @Test
    void createLocalUserUsernameConflictMapsToConflict() {
        when(passwordEncoderProvider.getIfAvailable()).thenReturn(passwordEncoder);
        when(passwordEncoder.encode(anyString())).thenReturn("$2y$10$hash");
        // sys_user 插入撞唯一约束 → 业务化为 409（事务回滚由 @Transactional 保证）
        when(sysUserMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("uq_sys_user"));

        ApiException ex = assertThrows(ApiException.class, () -> service.createLocalUser(
                new CreateLocalUserRequest("zhangsan", "zhang@example.com", "张三", "initpass1", List.of("MEMBER"))));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verifyNoInteractions(userCredentialMapper, tenantMemberMapper, tenantMemberRoleMapper);
    }

    // ---------- setRoles（守卫：自我保护/角色合法/最后管理员；覆盖式替换） ----------

    @Test
    void setRolesReplacesRowsAfterGuards() {
        // 目标用户 9 当前持 TENANT_ADMIN，替换为 MEMBER；租户内还有 5 名 ACTIVE 管理员 → 放行
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList()))
                .thenReturn(List.of(roleRow(9L, "TENANT_ADMIN")))   // 第一次读：守卫判定旧角色
                .thenReturn(List.of(roleRow(9L, "MEMBER")));        // 第二次读：loadUserVo 组装新视图
        when(userAccountMapper.countActiveTenantAdmins(1L)).thenReturn(5L);
        when(userAccountMapper.selectMemberOrgs(eq(1L), anyList())).thenReturn(List.of());
        when(userAccountMapper.insertAuditLog(eq(1L), eq(1L), anyString(), anyString())).thenReturn(1);

        UserVo vo = service.setRoles(9L, List.of("MEMBER"));

        assertEquals(List.of("MEMBER"), vo.roles());
        // 覆盖式替换：先物理清空全部角色行，再按入参插入
        verify(tenantMemberRoleMapper).hardDeleteByTenantAndUser(1L, 9L);
        ArgumentCaptor<TenantMemberRole> roleCaptor = ArgumentCaptor.forClass(TenantMemberRole.class);
        verify(tenantMemberRoleMapper).insert(roleCaptor.capture());
        assertEquals("MEMBER", roleCaptor.getValue().getRole());
        verify(userAccountMapper).insertAuditLog(1L, 1L, "tenant_member.set_roles", "9");
    }

    @Test
    void setRolesSelfModificationForbidden() {
        // 操作者 userId=1（JWT），目标也是 1 → 拒绝（无任何表操作）
        ApiException ex = assertThrows(ApiException.class, () -> service.setRoles(1L, List.of("MEMBER")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verifyNoInteractions(tenantMemberRoleMapper);
    }

    @Test
    void setRolesUnknownRoleRejected() {
        // 未定义角色码 → 400（防止越权注入绕过权限目录的角色）
        ApiException ex = assertThrows(ApiException.class, () -> service.setRoles(9L, List.of("SUPERROOT")));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verifyNoInteractions(tenantMemberRoleMapper);
    }

    @Test
    void setRolesLastTenantAdminProtected() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        // 目标是唯一一名 ACTIVE 管理员，替换会移除其 TENANT_ADMIN → 拒绝
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList()))
                .thenReturn(List.of(roleRow(9L, "TENANT_ADMIN")));
        when(userAccountMapper.countActiveTenantAdmins(1L)).thenReturn(1L);

        ApiException ex = assertThrows(ApiException.class, () -> service.setRoles(9L, List.of("MEMBER")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(tenantMemberRoleMapper, never()).hardDeleteByTenantAndUser(anyLong(), anyLong());
    }

    // ---------- removeFromTenant（守卫：自我保护/最后管理员；级联清理 + 审计） ----------

    @Test
    void removeFromTenantDeletesRelationAndWritesAudit() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList()))
                .thenReturn(List.of(roleRow(9L, "MEMBER")));    // 非 TENANT_ADMIN：不触发管理员保护
        when(userAccountMapper.insertAuditLog(eq(1L), eq(1L), anyString(), anyString())).thenReturn(1);
        when(tenantMemberMapper.hardDeleteByTenantAndUser(1L, 9L)).thenReturn(1);

        service.removeFromTenant(9L);

        // 物理删关系行（角色/org 关联经 FK CASCADE 级联），审计与删除同事务
        verify(tenantMemberMapper).hardDeleteByTenantAndUser(1L, 9L);
        verify(userAccountMapper).insertAuditLog(1L, 1L, "tenant_member.remove", "9");
    }

    @Test
    void removeFromTenantSelfForbidden() {
        // 操作者把自己移出租户 → 拒绝（无任何表操作）
        ApiException ex = assertThrows(ApiException.class, () -> service.removeFromTenant(1L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verifyNoInteractions(tenantMemberMapper);
    }

    @Test
    void removeFromTenantUnknownMemberNotFound() {
        // 目标不属于当前租户 → 404
        when(userAccountMapper.selectMembers(eq(1L), anyList())).thenReturn(List.of());
        ApiException ex = assertThrows(ApiException.class, () -> service.removeFromTenant(99L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(tenantMemberMapper, never()).hardDeleteByTenantAndUser(anyLong(), anyLong());
    }

    @Test
    void removeFromTenantLastTenantAdminProtected() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList()))
                .thenReturn(List.of(roleRow(9L, "TENANT_ADMIN")));
        when(userAccountMapper.countActiveTenantAdmins(1L)).thenReturn(1L);

        ApiException ex = assertThrows(ApiException.class, () -> service.removeFromTenant(9L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(tenantMemberMapper, never()).hardDeleteByTenantAndUser(anyLong(), anyLong());
    }

    // ---------- resetPassword（资源归属 + 统一强度 + 首登强制改密 + 审计） ----------

    /** 凭据记录样本（id=7，userId=9）。 */
    private UserCredentialStorePort.CredentialRecord credentialOfUser9() {
        return new UserCredentialStorePort.CredentialRecord(
                7L, 9L, "lisi", "$2y$10$old", "ACTIVE", 0, null,
                Instant.parse("2026-01-01T00:00:00Z"), null, false);
    }

    @Test
    void resetPasswordSetsMustChangeAndWritesAudit() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(credentialStore.findByUserId(9L)).thenReturn(Optional.of(credentialOfUser9()));
        when(passwordEncoderProvider.getIfAvailable()).thenReturn(passwordEncoder);
        when(passwordEncoder.encode("newpass1x")).thenReturn("$2y$10$new");
        when(userAccountMapper.insertAuditLog(eq(1L), eq(1L), anyString(), anyString())).thenReturn(1);

        service.resetPassword(9L, "newpass1x");

        // 重置语义：must_change_password=true（下次登录强制改回），过期时间按策略重算（非 null）
        verify(credentialStore).updatePassword(eq(7L), eq("$2y$10$new"),
                any(Instant.class), any(Instant.class), eq(true));
        verify(userAccountMapper).insertAuditLog(1L, 1L, "user_credential.reset_password", "9");
    }

    @Test
    void resetPasswordRequiresTenantMembership() {
        // 目标不属于当前租户 → 404（防跨租户重置他人密码的越权通道）
        when(userAccountMapper.selectMembers(eq(1L), anyList())).thenReturn(List.of());
        ApiException ex = assertThrows(ApiException.class, () -> service.resetPassword(99L, "newpass1x"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(credentialStore);
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(credentialStore.findByUserId(9L)).thenReturn(Optional.of(credentialOfUser9()));

        ApiException ex = assertThrows(ApiException.class, () -> service.resetPassword(9L, "short1"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(credentialStore, never()).updatePassword(anyLong(), anyString(), any(), any(), anyBoolean());
    }

    // ---------- updateUserOrg（clear-and-set + 组织合法性 + 资源归属） ----------

    @Test
    void updateUserOrgClearAndSetValidatedOrg() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(userAccountMapper.countOrgInTenant(1L, 5L)).thenReturn(1L);   // 组织存在且 ACTIVE
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList()))
                .thenReturn(List.of(roleRow(9L, "MEMBER")));
        when(userAccountMapper.selectMemberOrgs(eq(1L), anyList()))
                .thenReturn(List.of(orgRow(9L, "研发中心")));

        service.updateUserOrg(9L, 5L);

        verify(userAccountMapper).deleteMemberOrgs(1L, 9L);   // clear 步：清空旧关联
        verify(userAccountMapper).insertMemberOrg(1L, 9L, 5L); // set 步：写入新组织
    }

    @Test
    void updateUserOrgWithNullOrgOnlyClears() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(userAccountMapper.selectMemberRoles(eq(1L), anyList())).thenReturn(List.of());
        when(userAccountMapper.selectMemberOrgs(eq(1L), anyList())).thenReturn(List.of());

        service.updateUserOrg(9L, null);   // orgId=null：移出组织语义

        verify(userAccountMapper).deleteMemberOrgs(1L, 9L);
        verify(userAccountMapper, never()).insertMemberOrg(anyLong(), anyLong(), anyLong());
    }

    @Test
    void updateUserOrgUnknownOrgRejected() {
        when(userAccountMapper.selectMembers(eq(1L), anyList()))
                .thenReturn(List.of(row(9L, "李四", "li@example.com", "ACTIVE")));
        when(userAccountMapper.countOrgInTenant(1L, 5L)).thenReturn(0L);   // 组织不存在/停用/跨租户

        ApiException ex = assertThrows(ApiException.class, () -> service.updateUserOrg(9L, 5L));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(userAccountMapper, never()).insertMemberOrg(anyLong(), anyLong(), anyLong());
    }

    @Test
    void updateUserOrgNonMemberNotFound() {
        when(userAccountMapper.selectMembers(eq(1L), anyList())).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.updateUserOrg(99L, 5L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(userAccountMapper, never()).deleteMemberOrgs(anyLong(), anyLong());
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
