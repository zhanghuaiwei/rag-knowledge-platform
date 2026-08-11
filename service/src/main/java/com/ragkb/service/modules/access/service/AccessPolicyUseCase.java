package com.ragkb.service.modules.access.service;

import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.UserId;

/**
 * 统一授权用例占位。
 *
 * <p>TODO：根据 tenant、subject、KB 角色与文档 ACL 实现批量鉴权。
 */
public interface AccessPolicyUseCase {

    boolean canViewExcerpt(TenantId tenantId, UserId userId, long documentId);

    boolean canViewContent(TenantId tenantId, UserId userId, long documentId);

    boolean canDownloadOriginal(TenantId tenantId, UserId userId, long documentId);
}
