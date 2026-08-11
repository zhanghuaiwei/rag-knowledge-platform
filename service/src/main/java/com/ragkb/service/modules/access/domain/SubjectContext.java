package com.ragkb.service.modules.access.domain;

import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.UserId;

import java.time.Instant;
import java.util.List;

/**
 * 统一主体上下文（认证授权 §5.1）：认证成功后由认证层构造，业务方法不再直接解析
 * Cookie/JWT/API Key。字段由服务端从已验证身份推导，不接受客户端自报。
 *
 * @param actorType          主体类型（USER / API_KEY / SERVICE）
 * @param actorId            凭证主体 id（userId 或 apiKeyId）
 * @param subjectKey         issuer+subject 映射键（区分同全局用户的多 IdP 身份）
 * @param userId             全局用户 id（API_KEY 可为空）
 * @param activeTenantId     当前激活租户
 * @param tenantRoles        当前租户角色（粗粒度权限输入，非最终权限真相）
 * @param orgIds             组织归属
 * @param credentialScopes   凭证能力
 * @param policyVersion      策略版本（决策返回时用于比对缓存）
 * @param credentialExpiresAt 凭证到期时间
 */
public record SubjectContext(
        ActorType actorType,
        long actorId,
        String subjectKey,
        Long userId,
        long activeTenantId,
        List<String> tenantRoles,
        List<Long> orgIds,
        List<String> credentialScopes,
        long policyVersion,
        Instant credentialExpiresAt) {

    public enum ActorType { USER, API_KEY, SERVICE }

    /** 便捷：以 USER 主体 + 空角色构造（布尔便捷方法用；完整上下文由认证层构造）。 */
    public static SubjectContext user(TenantId tenantId, UserId userId) {
        return new SubjectContext(ActorType.USER, userId.value(), null, userId.value(),
                tenantId.value(), List.of(), List.of(), List.of(), 0L, null);
    }
}
