package com.ragkb.service.modules.knowledge.service.impl;

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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库用例桩实现（实现点由人工替换）。
 */
@Service
public class KbServiceImpl implements KbService {

    @Override
    public PageData<KbVo> listKbs(int page, int size) {
        return TodoSupport.notImplemented("KbService#listKbs");
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
    public KbVo createKb(KbCreateDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("KbService#createKb");
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
