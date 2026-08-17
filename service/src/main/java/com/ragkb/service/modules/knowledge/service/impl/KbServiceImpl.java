package com.ragkb.service.modules.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.modules.knowledge.persistence.entity.Kb;
import com.ragkb.service.modules.knowledge.persistence.mapper.KbMapper;
import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.knowledge.dto.CloneKbDto;
import com.ragkb.service.modules.knowledge.vo.IndexBuildVo;
import com.ragkb.service.modules.knowledge.vo.KbVo;
import com.ragkb.service.modules.knowledge.dto.KbCreateDto;
import com.ragkb.service.modules.knowledge.vo.KbMemberVo;
import com.ragkb.service.modules.knowledge.dto.KbMemberDto;
import com.ragkb.service.modules.knowledge.dto.KbUpdateDto;
import com.ragkb.service.modules.knowledge.service.KbService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库用例桩实现（实现点由人工替换）。
 */
@Service
public class KbServiceImpl implements KbService {

    @Autowired
    private KbMapper kbMapper;

    @Override
    public PageData<KbVo> listKbs(int page, int size) {
        // 分页返回kbs（selectPage 分页：配了 PaginationInnerInterceptor 会走 SQL 分页）
        IPage<Kb> kbPage = kbMapper.selectPage(new Page<>(page, size), null);
        List<KbVo> items = kbPage.getRecords().stream()
                .map(KbServiceImpl::toVo)
                .toList();
        return PageData.of(items, kbPage.getTotal(), page, size);
    }

    /** 实体 → VO 映射（桩：role/计数/索引配置名等跨表字段暂给默认值，业务实现时补齐）。 */
    private static KbVo toVo(Kb kb) {
        return new KbVo(
                kb.getId(),
                kb.getName(),
                kb.getDescription(),
                kb.getVisibility(),
                kb.getStatus(),
                null,                       // role：需当前用户在该知识库的角色（桩）
                0L,                         // documentCount（桩）
                0L,                         // chunkCount（桩）
                kb.getDataRegion(),
                null,                       // indexProfileName：需查 index_profile（桩）
                Boolean.TRUE.equals(kb.getRequiresReview()),
                Boolean.TRUE.equals(kb.getOcrEnabled()),
                kb.getCreateTime(),
                kb.getUpdateTime(),
                List.of());                 // members（桩）
    }

    @Override
    public KbVo getKb(long kbId) {
        Kb kb = kbMapper.selectById(kbId);
        if (kb == null) {
            // 统一异常：文档上传等跨模块校验依赖本方法判定「知识库不存在」。
            throw new ApiException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return toVo(kb);
    }

    @Override
    public KbService.KbBrief kbBrief(long kbId) {
        Kb kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        // 数据修复：tenant_id 为 null/0（旧数据/未归属租户）时就地修正为默认租户 1，
        // 保证 document 等子表的外键 (tenant_id, kb_id) 能匹配。
        if (kb.getTenantId() == null || kb.getTenantId() <= 0) {
            kb.setTenantId(1L);
            kbMapper.updateById(kb);
        }
        return new KbService.KbBrief(
                kb.getId(),
                kb.getTenantId(),
                kb.getName(), kb.getDataRegion(),
                kb.getStatus(), Boolean.TRUE.equals(kb.getRequiresReview()));
    }

    @Override
    public Map<Long, String> kbNamesByIds(Collection<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Map.of();
        }
        // 批量一次查询，避免 N+1（文档列表页需要批量回填 kbName）。
        return kbMapper.selectBatchIds(kbIds).stream()
                .collect(Collectors.toMap(Kb::getId, Kb::getName, (first, ignored) -> first));
    }

    @Override
    public KbVo updateKb(long kbId, KbUpdateDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("KbService#updateKb");
    }

    @Override
    public void createKb(KbCreateDto request, String idempotencyKey) {

        // 给kb表里新增知识库数据
        Kb kb = new Kb();
        BeanUtils.copyProperties(request, kb);
        // 必填字段兜底（DDL: tenant_id/index_profile_id NOT NULL，外键指向 sys_tenant/index_profile）
        // tenant_id：优先取当前认证主体租户，未认证时兜底默认租户 1（种子数据 sys_tenant(id=1)）
        Long tenantId = currentTenantIdOrNull();
        kb.setTenantId(tenantId != null ? tenantId : 1L);
        // index_profile_id：种子数据 index_profile(id=1, tenant_id=1) 为默认索引配置
        kb.setIndexProfileId(1L);
        // data_region / status / visibility 等有数据库 DEFAULT，但 BeanUtils.copyProperties 会把 null 覆盖进去
        // （MyBatis-Plus 默认不插入 null 字段，故 DEFAULT 仍生效；这里仅显式设关键值保险）
        if (kb.getDataRegion() == null) {
            kb.setDataRegion("default");
        }
        if (kb.getStatus() == null) {
            kb.setStatus("ACTIVE");
        }
        if (kb.getVisibility() == null) {
            kb.setVisibility("PRIVATE");
        }
        if (kb.getRequiresReview() == null) {
            kb.setRequiresReview(true);
        }
        if (kb.getOcrEnabled() == null) {
            kb.setOcrEnabled(true);
        }
        kbMapper.insert(kb);
    }

    @Override
    public Task cloneKb(long kbId, CloneKbDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("KbService#cloneKb");
    }

    @Override
    public KbVo archiveKb(long kbId) {
        return TodoSupport.notImplemented("KbService#archiveKb");
    }

    @Override
    public Task deleteKb(long kbId, String idempotencyKey) {
        return TodoSupport.notImplemented("KbService#deleteKb");
    }

    @Override
    public List<KbMemberVo> listKbMembers(long kbId) {
        return TodoSupport.notImplemented("KbService#listKbMembers");
    }

    @Override
    public KbMemberVo addOrUpdateKbMember(long kbId, KbMemberDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("KbService#addOrUpdateKbMember");
    }

    @Override
    public void removeKbMember(long kbId, long userId) {
        TodoSupport.notImplemented("KbService#removeKbMember");
    }

    @Override
    public List<IndexBuildVo> listIndexBuilds(long kbId, int page, int size) {
        return TodoSupport.notImplemented("KbService#listIndexBuilds");
    }

    @Override
    public Task triggerIndexBuild(long kbId, String idempotencyKey) {
        return TodoSupport.notImplemented("KbService#triggerIndexBuild");
    }

    @Override
    public IndexBuildVo getIndexBuild(long buildId) {
        return TodoSupport.notImplemented("KbService#getIndexBuild");
    }

    /** 当前 JWT 主体的租户 id；dev/API Key/未认证返回 null。 */
    private Long currentTenantIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal
                && principal.tenantId() > 0) {
            return principal.tenantId();
        }
        return null;
    }
}
