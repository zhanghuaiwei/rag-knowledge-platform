package com.ragkb.service.modules.knowledge.port;

import java.util.Optional;

/**
 * 知识库访问查询端口（供 access 模块统一授权使用）。
 *
 * <p>跨模块数据访问只能经本端口（PackageStructureTest：模块间仅经 Service/Port），
 * 由 knowledge 模块内部实现（KbMemberMapper）。无成员关系返回空（默认拒绝）。
 */
public interface KbAccessPort {

    /** 用户在指定知识库的角色（OWNER/EDITOR/VIEWER）；非成员返回空。 */
    Optional<String> roleOf(long tenantId, long userId, long kbId);
}
