package com.ragkb.service.modules.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragkb.service.modules.knowledge.persistence.entity.Kb;
import com.ragkb.service.modules.knowledge.persistence.mapper.KbMapper;
import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.model.Task;
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
import org.springframework.stereotype.Service;

import java.util.List;

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
        return TodoSupport.notImplemented("KbService#getKb");
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
}
