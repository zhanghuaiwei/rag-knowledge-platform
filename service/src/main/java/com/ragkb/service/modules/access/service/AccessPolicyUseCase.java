package com.ragkb.service.modules.access.service;

import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.UserId;
import com.ragkb.service.modules.access.domain.AccessDecision;
import com.ragkb.service.modules.access.domain.DocumentPermission;
import com.ragkb.service.modules.access.domain.SubjectContext;

/**
 * 统一授权用例（Policy Decision Point，认证授权 §5.2 / §5.4）。
 *
 * <p>默认拒绝：KB 角色（kb_member）→ 文档 ACL（document_acl）→ 文档发布/禁用/删除状态
 * 逐层判定，返回 allow/deny + reasonCode + policyVersion；权限蕴含
 * （DOWNLOAD_ORIGINAL→VIEW_CONTENT→VIEW_EXCERPT）由本层统一展开，调用方不得各自推断。
 *
 * <p>⚠️ 资源级决策依赖数据库（kb_member/document_acl/document），
 * db.enabled=true 时由 {@link AccessPolicyServiceImpl} 提供；否则回退默认拒绝。
 */
public interface AccessPolicyUseCase {

    /**
     * 文档级决策：判定主体在指定文档上是否具备所请求权限。
     *
     * @param subject   统一主体上下文（认证层构造）
     * @param documentId 文档 id（租户隔离）
     * @param requested 请求的文档权限档位
     */
    AccessDecision decideDocument(SubjectContext subject, long documentId, DocumentPermission requested);

    /** 便捷：是否可以看摘要。 */
    boolean canViewExcerpt(TenantId tenantId, UserId userId, long documentId);

    /** 便捷：是否可以看正文。 */
    boolean canViewContent(TenantId tenantId, UserId userId, long documentId);

    /** 便捷：是否可以下载原件。 */
    boolean canDownloadOriginal(TenantId tenantId, UserId userId, long documentId);
}
