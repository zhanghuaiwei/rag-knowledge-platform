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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 知识库域用例（实现点由人工完成）。
 */
public interface KbService {

    /**
     * 知识库最小视图：供跨模块（如文档上传校验目标库）使用，
     * 避免其他模块反向依赖本模块 vo 类型（模块间仅经 Service/Port 协作）。
     *
     * <p>{@code tenantId} 为 0 表示该知识库未归属具体租户（dev/种子数据场景），
     * 调用方需容忍 0 值，不得直接构造 {@code TenantId}（其要求 >0）。
     */
    record KbBrief(long id, long tenantId, String name, String dataRegion,
                   String status, boolean requiresReview) {
    }

    PageData<KbVo> listKbs(int page, int size);

    /** 查询单个知识库；不存在抛 {@code NOT_FOUND}。 */
    KbVo getKb(long kbId);

    /** 查询知识库最小视图（含租户/状态/数据地域）；不存在抛 {@code NOT_FOUND}。 */
    KbBrief kbBrief(long kbId);

    /** 批量查询知识库 id → 名称（供文档列表等跨模块展示；不存在的 id 不包含在结果中）。 */
    Map<Long, String> kbNamesByIds(Collection<Long> kbIds);

    KbVo updateKb(long kbId, KbUpdateDto request, String idempotencyKey);

    void createKb(KbCreateDto request, String idempotencyKey);

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
