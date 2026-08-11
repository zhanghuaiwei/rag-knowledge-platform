package com.ragkb.service.application.impl;

import com.ragkb.service.application.KbService;
import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.common.PageData;
import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.KbDtos.CloneKbRequest;
import com.ragkb.service.interfaces.dto.KbDtos.IndexBuild;
import com.ragkb.service.interfaces.dto.KbDtos.Kb;
import com.ragkb.service.interfaces.dto.KbDtos.KbCreateRequest;
import com.ragkb.service.interfaces.dto.KbDtos.KbMember;
import com.ragkb.service.interfaces.dto.KbDtos.KbMemberRequest;
import com.ragkb.service.interfaces.dto.KbDtos.KbUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库用例桩实现（实现点由人工替换）。
 */
@Service
public class KbServiceImpl implements KbService {

    @Override
    public PageData<Kb> listKbs(int page, int size) {
        return NotYetImplemented.stub("KbService#listKbs");
    }

    @Override
    public Kb getKb(long kbId) {
        return NotYetImplemented.stub("KbService#getKb");
    }

    @Override
    public Kb updateKb(long kbId, KbUpdateRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("KbService#updateKb");
    }

    @Override
    public Kb createKb(KbCreateRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("KbService#createKb");
    }

    @Override
    public Task cloneKb(long kbId, CloneKbRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("KbService#cloneKb");
    }

    @Override
    public Kb archiveKb(long kbId) {
        return NotYetImplemented.stub("KbService#archiveKb");
    }

    @Override
    public Task deleteKb(long kbId, String idempotencyKey) {
        return NotYetImplemented.stub("KbService#deleteKb");
    }

    @Override
    public List<KbMember> listKbMembers(long kbId) {
        return NotYetImplemented.stub("KbService#listKbMembers");
    }

    @Override
    public KbMember addOrUpdateKbMember(long kbId, KbMemberRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("KbService#addOrUpdateKbMember");
    }

    @Override
    public void removeKbMember(long kbId, long userId) {
        NotYetImplemented.stub("KbService#removeKbMember");
    }

    @Override
    public List<IndexBuild> listIndexBuilds(long kbId, int page, int size) {
        return NotYetImplemented.stub("KbService#listIndexBuilds");
    }

    @Override
    public Task triggerIndexBuild(long kbId, String idempotencyKey) {
        return NotYetImplemented.stub("KbService#triggerIndexBuild");
    }

    @Override
    public IndexBuild getIndexBuild(long buildId) {
        return NotYetImplemented.stub("KbService#getIndexBuild");
    }
}
