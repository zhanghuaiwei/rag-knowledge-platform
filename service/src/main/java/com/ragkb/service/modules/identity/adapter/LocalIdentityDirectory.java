package com.ragkb.service.modules.identity.adapter;

import com.ragkb.service.config.IdentityConditions;
import com.ragkb.service.modules.identity.port.IdentityDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * form 模式**无数据库兜底**（开发/演示）身份目录：dev 内存用户 → 全局用户 + 默认租户。
 *
 * <p>仅在 {@code ragkb.db.enabled=false}（默认，脚手架可无库启动）且 mode=form 时激活。
 * dev 用户来自 {@code ragkb.auth.dev.username / .roles}（与 SecurityConfig 的内存
 * {@code UserDetailsService} 同源），固定映射为 userId=1、默认租户 id=1/code=default。
 * 数据库启用后由 {@code JdbcIdentityDirectory} 接管（form|username 与 issuer|subject 均从库读取）。
 */
@Component
@Conditional(IdentityConditions.NoDbFormMode.class)
public class LocalIdentityDirectory implements IdentityDirectory {

    private static final String SUBJECT_PREFIX = "form|";
    private static final long DEV_USER_ID = 1L;
    private static final long DEFAULT_TENANT_ID = 1L;
    private static final String DEFAULT_TENANT_CODE = "default";
    private static final String DEFAULT_TENANT_NAME = "默认租户";

    private final String devUsername;
    private final List<String> devRoles;

    public LocalIdentityDirectory(
            @Value("${ragkb.auth.dev.username:admin}") String devUsername,
            @Value("${ragkb.auth.dev.roles:TENANT_ADMIN}") String devRoles) {
        this.devUsername = devUsername;
        this.devRoles = Arrays.stream(devRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }

    @Override
    public Optional<ResolvedIdentity> resolveBySubjectKey(String subjectKey) {
        if ((SUBJECT_PREFIX + devUsername).equals(subjectKey)) {
            return Optional.of(new ResolvedIdentity(
                    DEV_USER_ID, subjectKey, devUsername, devUsername + "@ragkb.dev"));
        }
        return Optional.empty();
    }

    @Override
    public List<TenantMembership> memberships(long userId) {
        if (userId == DEV_USER_ID) {
            return List.of(new TenantMembership(
                    DEFAULT_TENANT_ID, DEFAULT_TENANT_CODE, DEFAULT_TENANT_NAME, "ACTIVE", devRoles, 1L));
        }
        return List.of();
    }

    @Override
    public Optional<TenantMembership> membership(long userId, long tenantId) {
        return memberships(userId).stream()
                .filter(membership -> membership.tenantId() == tenantId
                        && "ACTIVE".equals(membership.status()))
                .findFirst();
    }
}
