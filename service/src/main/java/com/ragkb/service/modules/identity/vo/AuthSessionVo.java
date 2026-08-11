package com.ragkb.service.modules.identity.vo;

import java.util.List;

/**
 * 当前登录用户会话响应视图（动态菜单的授权上下文）。
 *
 * <p>字段语义（认证授权 §5.1 / 动态菜单 §5.1，OpenAPI 评审点）：
 * <ul>
 *   <li>{@code tenantRoles}：当前租户角色，用于解释身份，不直接散落到菜单判断；</li>
 *   <li>{@code credentialScopes}：凭证能力（如 web / API Key scope），不等于租户角色与最终权限；</li>
 *   <li>{@code permissions}：菜单/路由/按钮使用的稳定能力码（服务端集中聚合，见 PermissionCatalog）；</li>
 *   <li>{@code features}：租户套餐、部署模式或后端能力是否可用；</li>
 *   <li>{@code policyVersion}：授权策略版本，用于判断前端缓存上下文是否过期。</li>
 * </ul>
 * 前端按本视图过滤菜单仅改善体验，真正的 API 授权仍由服务端执行。
 */
public record AuthSessionVo(
        long userId,
        String subjectKey,
        String displayName,
        TenantContextVo activeTenant,
        List<TenantContextVo> tenants,
        List<String> tenantRoles,
        List<String> credentialScopes,
        List<String> permissions,
        List<String> features,
        long policyVersion) {
}
