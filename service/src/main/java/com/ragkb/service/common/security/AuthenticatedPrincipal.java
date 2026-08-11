package com.ragkb.service.common.security;

/**
 * 认证主体在 {@code SecurityContext} 中的通用契约，供公共设施（如审计字段自动填充）读取当前用户。
 *
 * <p>各认证模式的 principal 实现本接口并返回全局用户 id：用户态返回 {@code userId}；
 * API Key / 系统任务无全局用户，返回 0（此时 create_by/update_by 留空，由系统写入）。
 * 放在 {@code common} 层，避免公共组件反向依赖 {@code modules/identity}。
 */
public interface AuthenticatedPrincipal {

    /** 全局用户 id；非用户态主体返回 0。 */
    long authenticatedUserId();
}
