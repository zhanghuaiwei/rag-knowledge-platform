package com.ragkb.service.application;

import com.ragkb.service.common.PageData;
import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.KbDtos.CloneKbRequest;
import com.ragkb.service.interfaces.dto.KbDtos.IndexBuild;
import com.ragkb.service.interfaces.dto.KbDtos.Kb;
import com.ragkb.service.interfaces.dto.KbDtos.KbCreateRequest;
import com.ragkb.service.interfaces.dto.KbDtos.KbMember;
import com.ragkb.service.interfaces.dto.KbDtos.KbMemberRequest;
import com.ragkb.service.interfaces.dto.KbDtos.KbUpdateRequest;

import java.util.List;

/**
 * 知识库域用例（实现点由人工完成）。
 */
public interface KbService {

    PageData<Kb> listKbs(int page, int size);

    Kb getKb(long kbId);

    Kb updateKb(long kbId, KbUpdateRequest request, String idempotencyKey);

    Kb createKb(KbCreateRequest request, String idempotencyKey);

    /** 克隆为异步任务；返回任务，前端轮询进度后按 resourceId 获取新库。 */
    Task cloneKb(long kbId, CloneKbRequest request, String idempotencyKey);

    /** 归档：status → ARCHIVED。 */
    Kb archiveKb(long kbId);

    /** 软删除为异步任务。 */
    Task deleteKb(long kbId, String idempotencyKey);

    List<KbMember> listKbMembers(long kbId);

    KbMember addOrUpdateKbMember(long kbId, KbMemberRequest request, String idempotencyKey);

    void removeKbMember(long kbId, long userId);

    List<IndexBuild> listIndexBuilds(long kbId, int page, int size);

    Task triggerIndexBuild(long kbId, String idempotencyKey);

    IndexBuild getIndexBuild(long buildId);
}
