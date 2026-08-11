package com.ragkb.service.modules.identity.vo;

import java.util.List;

/**
 * 租户上下文响应视图（服务端从已验证身份推导，不信任客户端自报）。
 *
 * <p>{@code tenantRoles} 是当前用户在该租户的角色集合（解释身份用，不直接做菜单判断；
 * 菜单/按钮用 {@link AuthSessionVo#permissions} 的稳定能力码）。
 */
public record TenantContextVo(long tenantId, String tenantCode, List<String> tenantRoles) {
}
