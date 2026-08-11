package com.ragkb.service.modules.knowledge.service;

import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.knowledge.dto.CloneKbDto;
import com.ragkb.service.modules.knowledge.vo.IndexBuildVo;
import com.ragkb.service.modules.knowledge.vo.KbVo;
import com.ragkb.service.modules.knowledge.dto.KbCreateDto;
import com.ragkb.service.modules.knowledge.vo.KbMemberVo;
import com.ragkb.service.modules.knowledge.dto.KbMemberDto;
import com.ragkb.service.modules.knowledge.dto.KbUpdateDto;

import java.util.List;

/**
 * 知识库域用例（实现点由人工完成）。
 */
public interface KbService {

    PageData<KbVo> listKbs(int page, int size);

    KbVo getKb(long kbId);

    KbVo updateKb(long kbId, KbUpdateDto request, String idempotencyKey);

    KbVo createKb(KbCreateDto request, String idempotencyKey);

    /** 克隆为异步任务；返回任务，前端轮询进度后按 resourceId 获取新库。 */
    Task cloneKb(long kbId, CloneKbDto request, String idempotencyKey);

    /** 归档：status → ARCHIVED。 */
    KbVo archiveKb(long kbId);

    /** 软删除为异步任务。 */
    Task deleteKb(long kbId, String idempotencyKey);

    List<KbMemberVo> listKbMembers(long kbId);

    KbMemberVo addOrUpdateKbMember(long kbId, KbMemberDto request, String idempotencyKey);

    void removeKbMember(long kbId, long userId);

    List<IndexBuildVo> listIndexBuilds(long kbId, int page, int size);

    Task triggerIndexBuild(long kbId, String idempotencyKey);

    IndexBuildVo getIndexBuild(long buildId);
}
