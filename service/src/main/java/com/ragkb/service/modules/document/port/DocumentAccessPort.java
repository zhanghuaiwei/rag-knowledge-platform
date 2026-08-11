package com.ragkb.service.modules.document.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文档访问查询端口（供 access 模块统一授权使用）。
 *
 * <p>跨模块数据访问只能经本端口（PackageStructureTest：模块间仅经 Service/Port），
 * 由 document 模块内部实现（DocumentMapper + DocumentAclMapper）。
 */
public interface DocumentAccessPort {

    /** 文档访问视图：所属 KB、生命周期状态、ACL 行与策略版本；不存在返回空。 */
    Optional<DocumentAccessView> viewOf(long tenantId, long documentId);

    /** 文档访问决策所需的最小信息集。 */
    record DocumentAccessView(long documentId, long kbId, String lifecycleStatus, Boolean isDisabled,
                              Integer delFlag, Long policyVersion, List<AclEntry> acls) {
    }

    /** 文档 ACL 行（principal_type / principal_key / permission）。 */
    record AclEntry(String principalType, String principalKey, String permission) {
    }
}
