package com.ragkb.service.modules.knowledge.vo;

import java.time.Instant;

/**
 * 知识库成员响应视图（对齐前端 Kb 成员契约）。
 */
public record KbMemberVo(long userId, String userName, String role, Instant joinedAt) {
}
