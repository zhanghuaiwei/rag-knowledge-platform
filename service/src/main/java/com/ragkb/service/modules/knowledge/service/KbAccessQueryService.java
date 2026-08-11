package com.ragkb.service.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragkb.service.modules.knowledge.persistence.entity.KbMember;
import com.ragkb.service.modules.knowledge.persistence.mapper.KbMemberMapper;
import com.ragkb.service.modules.knowledge.port.KbAccessPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link KbAccessPort} 的数据库实现（db.enabled=true 时激活）。
 * 单表查询由 BaseMapper 提供，无 N+1。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class KbAccessQueryService implements KbAccessPort {

    private final KbMemberMapper kbMemberMapper;

    public KbAccessQueryService(KbMemberMapper kbMemberMapper) {
        this.kbMemberMapper = kbMemberMapper;
    }

    @Override
    public Optional<String> roleOf(long tenantId, long userId, long kbId) {
        KbMember member = kbMemberMapper.selectOne(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId)
                .last("LIMIT 1"));
        return Optional.ofNullable(member).map(KbMember::getRole);
    }
}
