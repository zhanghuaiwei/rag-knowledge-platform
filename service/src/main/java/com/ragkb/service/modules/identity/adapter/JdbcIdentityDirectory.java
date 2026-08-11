package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.modules.identity.port.IdentityDirectory;
import com.ragkb.service.util.TodoSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * OIDC 生产身份目录 —— ⚠️ 人工实现点（谨慎区，按 03-详细设计）。
 *
 * <p>实现要点（本轮不实现，仅占位保证 oidc 模式 Spring 装配完整）：
 * <ul>
 *   <li>{@code resolveBySubjectKey}：按 {@code issuer|subject} 查 {@code identity_account} → {@code sys_user}；</li>
 *   <li>{@code memberships}：查 {@code tenant_member(status=ACTIVE)} + {@code tenant_member_role} + {@code sys_tenant}；</li>
 *   <li>policyVersion 取 {@code sys_tenant.policy_version}。</li>
 * </ul>
 * 使用 MyBatis-Plus Mapper（db.enabled=true），跨模块访问走各模块 Service/Port。
 */
@Component
@ConditionalOnProperty(name = "ragkb.auth.mode", havingValue = "oidc")
public class JdbcIdentityDirectory implements IdentityDirectory {

    @Override
    public Optional<ResolvedIdentity> resolveBySubjectKey(String subjectKey) {
        return TodoSupport.notImplemented("JdbcIdentityDirectory#resolveBySubjectKey");
    }

    @Override
    public List<TenantMembership> memberships(long userId) {
        return TodoSupport.notImplemented("JdbcIdentityDirectory#memberships");
    }

    @Override
    public Optional<TenantMembership> membership(long userId, long tenantId) {
        return TodoSupport.notImplemented("JdbcIdentityDirectory#membership");
    }
}
