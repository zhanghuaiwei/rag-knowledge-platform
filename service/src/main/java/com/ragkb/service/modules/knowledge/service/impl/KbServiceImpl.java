package com.ragkb.service.modules.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.security.SecurityUtils;
import com.ragkb.service.modules.document.service.DocumentService;
import com.ragkb.service.modules.identity.service.UserAccountService;
import com.ragkb.service.modules.knowledge.persistence.entity.IndexBuild;
import com.ragkb.service.modules.knowledge.persistence.entity.IndexProfile;
import com.ragkb.service.modules.knowledge.persistence.entity.Kb;
import com.ragkb.service.modules.knowledge.persistence.entity.KbMember;
import com.ragkb.service.modules.knowledge.persistence.mapper.IndexBuildMapper;
import com.ragkb.service.modules.knowledge.persistence.mapper.IndexProfileMapper;
import com.ragkb.service.modules.knowledge.persistence.mapper.KbMapper;
import com.ragkb.service.modules.knowledge.persistence.mapper.KbMemberMapper;
import com.ragkb.service.modules.knowledge.vo.IndexBuildVo;
import com.ragkb.service.modules.knowledge.vo.KbVo;
import com.ragkb.service.modules.knowledge.dto.KbCreateDto;
import com.ragkb.service.modules.knowledge.vo.KbMemberVo;
import com.ragkb.service.modules.knowledge.dto.KbMemberDto;
import com.ragkb.service.modules.knowledge.dto.KbUpdateDto;
import com.ragkb.service.modules.knowledge.dto.CloneKbDto;
import com.ragkb.service.modules.knowledge.service.KbService;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.rag.port.RagEnginePort;
import com.ragkb.service.modules.task.service.TaskService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 知识库域用例实现：生命周期（更新/归档/删除/克隆）+ 成员管理 + 索引构建登记。
 *
 * <p>业务规则来源：{@code deploy/ddl/init.sql} 的 CHECK 约束、
 * {@code web/api-client} 的前端契约，以及 {@code KbController} 的端点语义。
 *
 * <p>多租户隔离（deny-by-default）：所有读写先经 {@link #requireKb} 校验
 * 「资源存在 + 当前认证租户与库归属租户一致」；成员/构建子表查询一律带 tenant_id。
 *
 * <p>跨模块协作只经 Service/Port（PackageStructureTest 约束）：
 * document 模块经 {@link DocumentService}（级联软删/计数），identity 模块经
 * {@link UserAccountService}（成员显示名），rag-engine 经 {@link RagEnginePort}（向量清理）。
 * 其中 DocumentService/UserAccountService 用 {@link ObjectProvider} 懒获取：
 * DocumentServiceImpl 构造期已注入 KbService（会形成环），懒获取打破启动期循环依赖。
 */
// 装配条件：本实现依赖 MyBatis Mapper（仅 ragkb.db.enabled=true 时注册），与所属 Controller
// 按同一开关条件装配，避免 scaffold 模式（无数据库）上下文装配失败。
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class KbServiceImpl implements KbService {

    private static final Logger log = LoggerFactory.getLogger(KbServiceImpl.class);

    // ---------- 状态/枚举常量（与 DDL CHECK 约束一一对应，禁止裸写魔法值扩散） ----------

    /** kb.status：正常态。 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** kb.status：软归档（不可上传/问答，可恢复）。 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    /** kb.status：删除中（预留两段式删除的中间态）。 */
    private static final String STATUS_DELETING = "DELETING";
    /** kb.status：已软删（终态，列表默认不可见）。 */
    private static final String STATUS_DELETED = "DELETED";
    /** kb.visibility 白名单（ck_kb_visibility）。 */
    private static final List<String> VISIBILITIES = List.of("PRIVATE", "TENANT");
    /** PATCH 允许流转的 kb.status（DELETING/DELETED 只能经删除端点进入，不开放 PATCH）。 */
    private static final List<String> PATCHABLE_STATUSES = List.of(STATUS_ACTIVE, STATUS_ARCHIVED);
    /** kb_member.role 白名单（ck_kb_member_role）。 */
    private static final String KB_ROLE_OWNER = "OWNER";
    private static final List<String> KB_ROLES = List.of(KB_ROLE_OWNER, "EDITOR", "VIEWER");
    /** index_build.status 初始态（后续由索引构建 worker 推进 BUILDING→…→PUBLISHED）。 */
    private static final String BUILD_STATUS_QUEUED = "QUEUED";
    /** 历史数据 tenant_id 为 0/null（未归属租户）时兜底使用的默认租户（与 kbBrief 修复逻辑一致）。 */
    private static final long DEFAULT_TENANT_ID = 1L;

    // ---------- 依赖（本模块持久化 + 跨模块 Service/Port） ----------

    @Autowired
    private KbMapper kbMapper;

    /** 成员表读写（kb_member 属 knowledge 模块自有持久化）。 */
    @Autowired
    private KbMemberMapper kbMemberMapper;

    /** 索引构建记录表（index_build）。 */
    @Autowired
    private IndexBuildMapper indexBuildMapper;

    /** 索引配置表（index_profile，构建记录需要回填 profileVersion）。 */
    @Autowired
    private IndexProfileMapper indexProfileMapper;

    /** 异步任务登记（202 响应体，前端轮询 tasks/{id}）。 */
    @Autowired
    private TaskService taskService;

    /** rag-engine 端口：删除知识库后按文档清理向量。 */
    @Autowired
    private RagEnginePort ragEnginePort;

    /** quality_report JSONB（映射为 String）→ IndexBuildVo.qualityGate 对象的解析器。 */
    @Autowired
    private ObjectMapper objectMapper;

    /** 文档模块服务（懒获取避免与 DocumentServiceImpl→KbService 的构造环）。 */
    @Autowired
    private ObjectProvider<DocumentService> documentServiceProvider;

    /** 身份模块服务（懒获取；db.enabled=false 时无实现 bean，需判空降级）。 */
    @Autowired
    private ObjectProvider<UserAccountService> userAccountServiceProvider;

    /**
     * 向量清理单线程执行器：删除知识库的向量回收属旁路任务，不占用请求线程
     * （守护线程模式对齐 UploadSessionStore 的清理线程约定）。
     */
    private final ExecutorService vectorCleanupExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "kb-vector-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    /** 应用停机时拒绝新清理任务并尽量排空已提交任务（未执行完的向量留待补偿任务回收）。 */
    @PreDestroy
    public void shutdownVectorCleanup() {
        vectorCleanupExecutor.shutdown();
    }

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
    @Transactional
    public KbVo updateKb(long kbId, KbUpdateDto request, String idempotencyKey) {
        // ① 加载并校验目标库（存在性 + 租户归属，deny-by-default）。
        Kb kb = requireKb(kbId);
        // 删除中/已删除为终态保护：不允许再编辑基础信息。
        if (STATUS_DELETING.equals(kb.getStatus()) || STATUS_DELETED.equals(kb.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库已删除或正在删除，无法编辑");
        }
        boolean hasStatusChange = StringUtils.hasText(request.status());
        // 归档库冻结编辑；唯一例外是携带 status=ACTIVE 的恢复操作（与归档端点语义对称）。
        if (STATUS_ARCHIVED.equals(kb.getStatus()) && !hasStatusChange) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库已归档，请先恢复为运行中再编辑");
        }
        // ② 枚举白名单校验（对齐 DDL CHECK，拦截脏值直接落库）。
        if (request.visibility() != null && !VISIBILITIES.contains(request.visibility())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的可见性: " + request.visibility());
        }
        if (hasStatusChange && !PATCHABLE_STATUSES.contains(request.status())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的状态变更: " + request.status());
        }
        // ③ 名称变更做租户内唯一性预检（uq_kb_tenant_name 兜底并发窗口）。
        if (StringUtils.hasText(request.name())) {
            String name = request.name().trim();
            Long sameNameCount = kbMapper.selectCount(new LambdaQueryWrapper<Kb>()
                    .eq(Kb::getTenantId, kb.getTenantId())
                    .ne(Kb::getId, kbId)
                    .eq(Kb::getName, name));
            if (sameNameCount != null && sameNameCount > 0) {
                throw new ApiException(ErrorCode.CONFLICT, "同名知识库已存在: " + name);
            }
            kb.setName(name);
        }
        // ④ PATCH 语义：仅覆盖非空入参（治理归属 metadataSchema/retention 与索引配置不在契约字段内，不可经本端点变更）。
        if (request.description() != null) {
            kb.setDescription(request.description());
        }
        if (request.visibility() != null) {
            kb.setVisibility(request.visibility());
        }
        if (request.requiresReview() != null) {
            kb.setRequiresReview(request.requiresReview());
        }
        if (request.ocrEnabled() != null) {
            kb.setOcrEnabled(request.ocrEnabled());
        }
        if (hasStatusChange) {
            kb.setStatus(request.status());
        }
        // ⑤ 实体携带 row_version 走乐观锁更新（OptimisticLockerInnerInterceptor）；0 行即并发冲突。
        if (kbMapper.updateById(kb) == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库已被他人修改，请刷新后重试");
        }
        return toDetailVo(kb);
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
    @Transactional
    public Task cloneKb(long kbId, CloneKbDto request, String idempotencyKey) {
        // ① 源库必须存在且属于当前租户。
        Kb source = requireKb(kbId);
        // 删除中/已删除的库不允许克隆（避免复制出脏数据）。
        if (STATUS_DELETING.equals(source.getStatus()) || STATUS_DELETED.equals(source.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库已删除或正在删除，无法克隆");
        }
        // ② 生成租户内不重复的副本名：缺省「原名（副本）」，冲突时追加（副本2）（副本3）…。
        String desiredName = StringUtils.hasText(request.name())
                ? request.name().trim()
                : source.getName() + "（副本）";
        String newName = uniqueCloneName(source.getTenantId(), desiredName);
        // ③ 复制元数据：id/审计字段/乐观锁/active_index_build_id 不继承；副本以全新 ACTIVE 状态起建。
        Kb copy = new Kb();
        copy.setTenantId(source.getTenantId());
        copy.setDataRegion(source.getDataRegion());
        copy.setName(newName);
        copy.setDescription(source.getDescription());
        copy.setVisibility(source.getVisibility());
        copy.setStatus(STATUS_ACTIVE);
        copy.setIndexProfileId(source.getIndexProfileId());
        copy.setMetadataSchemaId(source.getMetadataSchemaId());
        copy.setRetentionPolicyId(source.getRetentionPolicyId());
        copy.setRequiresReview(source.getRequiresReview());
        copy.setOcrEnabled(source.getOcrEnabled());
        copy.setPolicyVersion(source.getPolicyVersion() == null ? 1L : source.getPolicyVersion());
        try {
            kbMapper.insert(copy);
        } catch (DuplicateKeyException e) {
            // 并发克隆撞 uq_kb_tenant_name：提示改名重试（预检窗口外的兜底）。
            throw new ApiException(ErrorCode.CONFLICT, "同名知识库已存在，请指定副本名称");
        }
        // ④ 复制成员及角色：文档/对象存储/向量不复制（CloneKbDto 契约仅含 name，
        //    未定义文档复制策略；最小实现只复制元数据+成员，任务 message 如实说明）。
        List<KbMember> sourceMembers = kbMemberMapper.selectList(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, source.getTenantId())
                .eq(KbMember::getKbId, kbId));
        for (KbMember sourceMember : sourceMembers) {
            KbMember memberCopy = new KbMember();
            memberCopy.setTenantId(sourceMember.getTenantId());
            memberCopy.setKbId(copy.getId());
            memberCopy.setUserId(sourceMember.getUserId());
            memberCopy.setRole(sourceMember.getRole());
            kbMemberMapper.insert(memberCopy);
        }
        // ⑤ 前端 waitForTask 终态后按 resourceId 回读新库：同步完成即返回 SUCCEEDED（对齐 completeUpload 模式）。
        return taskService.submit("CLONE", "SUCCEEDED",
                "「" + source.getName() + "」克隆完成", 100,
                "KB", String.valueOf(copy.getId()),
                "已复制知识库配置与 " + sourceMembers.size() + " 名成员；文档与向量未复制，需在副本中重新上传");
    }

    @Override
    @Transactional
    public KbVo archiveKb(long kbId) {
        // ① 加载并校验目标库。
        Kb kb = requireKb(kbId);
        // 幂等：重复归档直接返回现状，不产生多余写放大。
        if (STATUS_ARCHIVED.equals(kb.getStatus())) {
            return toDetailVo(kb);
        }
        // 删除中/已删除的库不再接受归档（状态机单向）。
        if (STATUS_DELETING.equals(kb.getStatus()) || STATUS_DELETED.equals(kb.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库已删除或正在删除，无法归档");
        }
        // ② 软归档：仅置 status=ARCHIVED（del_flag 保持 0，满足 ck_kb_del_flag；
        //    归档后上传被 initUpload 的 ACTIVE 校验拦截，问答侧由检索链路按状态过滤）。
        kb.setStatus(STATUS_ARCHIVED);
        if (kbMapper.updateById(kb) == 0) {
            // 乐观锁冲突：并发归档/编辑时提示刷新重试。
            throw new ApiException(ErrorCode.CONFLICT, "知识库已被他人修改，请刷新后重试");
        }
        return toDetailVo(kb);
    }

    @Override
    @Transactional
    public Task deleteKb(long kbId, String idempotencyKey) {
        // ① 加载并校验目标库（危险操作的租户隔离入口）。
        Kb kb = requireKb(kbId);
        long tenantId = effectiveTenantId(kb);
        // 幂等重放：已删除的库直接返回完成态任务（前端重试/网络重发场景）。
        if (STATUS_DELETED.equals(kb.getStatus())) {
            return taskService.submit("DELETE", "SUCCEEDED",
                    "「" + kb.getName() + "」已删除（幂等重放）", 100,
                    "KB", String.valueOf(kbId), null);
        }
        // 删除中拒绝重复提交（防止双份清理任务）。
        if (STATUS_DELETING.equals(kb.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库删除进行中，请勿重复提交");
        }
        // 危险操作两段式防御：契约未提供二次确认字段，故强制「先归档再删除」，
        // 让删除动作必须经过一次显式的状态确认（fail-closed）。
        if (!STATUS_ARCHIVED.equals(kb.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "请先归档知识库后再删除");
        }
        // ② 知识库软删：status=DELETED + del_flag=1（ck_kb_del_flag 要求两列一致；
        //    del_flag 是 @TableLogic 字段、实体更新不落 SET，故用 wrapper setSql 显式置位）。
        int kbUpdated = kbMapper.update(null, new LambdaUpdateWrapper<Kb>()
                .eq(Kb::getId, kbId)
                .eq(Kb::getTenantId, tenantId)
                .set(Kb::getStatus, STATUS_DELETED)
                .setSql("del_flag = 1"));
        if (kbUpdated == 0) {
            // 0 行 = 并发已删或租户不匹配，按冲突处理。
            throw new ApiException(ErrorCode.CONFLICT, "知识库删除冲突，请刷新后重试");
        }
        // ③ 级联软删成员关系（@TableLogic 的 delete 自动转 UPDATE del_flag=1）。
        kbMemberMapper.delete(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kbId));
        // ④ 级联标记文档（lifecycle=DELETING + del_flag=1），并取回文档清单供向量清理（跨模块经 DocumentService）。
        List<Long> documentIds = documentServiceProvider.getObject().softDeleteDocumentsByKb(tenantId, kbId);
        // ⑤ 向量清理走异步旁路（不阻塞主流程）；注册到事务提交后触发，避免主事务回滚时向量已被物理清除。
        registerVectorCleanupAfterCommit(new TenantId(tenantId), kbId, documentIds);
        // ⑥ 受理即返回：软删已生效，向量清理由后台线程消化，任务 message 携带清理规模。
        return taskService.submit("DELETE", "SUCCEEDED",
                "「" + kb.getName() + "」删除已受理，" + documentIds.size() + " 篇文档向量异步清理中", 100,
                "KB", String.valueOf(kbId), null);
    }

    @Override
    public List<KbMemberVo> listKbMembers(long kbId) {
        // ① 校验库归属后按 (tenant, kb) 拉取成员（子表查询显式带租户，deny-by-default）。
        Kb kb = requireKb(kbId);
        long tenantId = effectiveTenantId(kb);
        List<KbMember> members = kbMemberMapper.selectList(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kbId)
                .orderByAsc(KbMember::getId));
        if (members.isEmpty()) {
            return List.of();
        }
        // ② 批量回填成员显示名（跨模块经 UserAccountService；未命中租户成员的用户名置空展示）。
        Map<Long, String> namesByUser = userNames(tenantId,
                members.stream().map(KbMember::getUserId).toList());
        // ③ joinedAt 取成员关系建立时间（kb_member.create_time）。
        return members.stream()
                .map(member -> new KbMemberVo(
                        member.getUserId(),
                        namesByUser.getOrDefault(member.getUserId(), ""),
                        member.getRole(),
                        member.getCreateTime()))
                .toList();
    }

    @Override
    @Transactional
    public KbMemberVo addOrUpdateKbMember(long kbId, KbMemberDto request, String idempotencyKey) {
        // ① 校验库归属；删除中/已删除的库不再接受成员变更。
        Kb kb = requireKb(kbId);
        long tenantId = effectiveTenantId(kb);
        if (STATUS_DELETING.equals(kb.getStatus()) || STATUS_DELETED.equals(kb.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库已删除或正在删除，无法变更成员");
        }
        // ② 角色白名单校验（ck_kb_member_role：OWNER/EDITOR/VIEWER）。
        if (!KB_ROLES.contains(request.role())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的成员角色: " + request.role());
        }
        // ③ 目标用户必须已是本租户成员（防止跨租户拉人；displayName 查询天然限定租户）。
        UserAccountService userAccountService = userAccountServiceIfAvailable();
        Map<Long, String> namesByUser = userAccountService != null
                ? userAccountService.displayNamesOf(tenantId, List.of(request.userId()))
                : Map.of();
        if (userAccountService != null && !namesByUser.containsKey(request.userId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "用户不属于当前租户，无法添加为成员");
        }
        // ④ 查既有成员关系：命中则改角色（更新语义），未命中则新增。
        KbMember existing = kbMemberMapper.selectOne(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, request.userId())
                .last("LIMIT 1"));
        if (existing == null) {
            // 新增成员关系行（uq_kb_member (tenant,kb,user) 唯一）。
            KbMember member = new KbMember();
            member.setTenantId(tenantId);
            member.setKbId(kbId);
            member.setUserId(request.userId());
            member.setRole(request.role());
            try {
                kbMemberMapper.insert(member);
            } catch (DuplicateKeyException e) {
                // 并发新增撞唯一约束：按已存在处理（提示刷新）。
                throw new ApiException(ErrorCode.CONFLICT, "该用户已是知识库成员，请刷新后重试");
            }
            return new KbMemberVo(member.getUserId(),
                    namesByUser.getOrDefault(member.getUserId(), ""),
                    member.getRole(), member.getCreateTime());
        }
        // 更新角色：同角色幂等返回，不产生多余写。
        if (!existing.getRole().equals(request.role())) {
            // 最后一名 OWNER 保护：OWNER 降级前必须存在其他 OWNER（保证库始终有责任人）。
            if (KB_ROLE_OWNER.equals(existing.getRole())
                    && !KB_ROLE_OWNER.equals(request.role())
                    && countOwners(tenantId, kbId) <= 1) {
                throw new ApiException(ErrorCode.CONFLICT, "不能降级最后一名 OWNER，请先转移所有权");
            }
            existing.setRole(request.role());
            if (kbMemberMapper.updateById(existing) == 0) {
                // 乐观锁冲突（row_version）：并发变更成员角色时提示重试。
                throw new ApiException(ErrorCode.CONFLICT, "成员信息已被他人修改，请刷新后重试");
            }
        }
        return new KbMemberVo(existing.getUserId(),
                namesByUser.getOrDefault(existing.getUserId(), ""),
                existing.getRole(), existing.getCreateTime());
    }

    @Override
    @Transactional
    public void removeKbMember(long kbId, long userId) {
        // ① 校验库归属后定位成员关系行。
        Kb kb = requireKb(kbId);
        long tenantId = effectiveTenantId(kb);
        KbMember member = kbMemberMapper.selectOne(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null) {
            // 不存在或已移除：统一按不存在处理（幂等语义交给上层重试）。
            throw new ApiException(ErrorCode.NOT_FOUND, "成员不存在或已移除");
        }
        // ② 最后一名 OWNER 不可移除（必须先转移所有权，保证知识库始终有责任人）。
        if (KB_ROLE_OWNER.equals(member.getRole()) && countOwners(tenantId, kbId) <= 1) {
            throw new ApiException(ErrorCode.CONFLICT, "不能移除最后一名 OWNER，请先转移所有权");
        }
        // ③ 软删成员关系（@TableLogic → UPDATE del_flag=1）。
        kbMemberMapper.deleteById(member.getId());
    }

    @Override
    public List<IndexBuildVo> listIndexBuilds(long kbId, int page, int size) {
        // ① 校验库归属后分页拉取构建历史（按构建序号倒序，最新在前）。
        Kb kb = requireKb(kbId);
        long tenantId = effectiveTenantId(kb);
        IPage<IndexBuild> buildPage = indexBuildMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<IndexBuild>()
                        .eq(IndexBuild::getTenantId, tenantId)
                        .eq(IndexBuild::getKbId, kbId)
                        .orderByDesc(IndexBuild::getBuildNo));
        List<IndexBuild> builds = buildPage.getRecords();
        if (builds.isEmpty()) {
            return List.of();
        }
        // ② 批量回填 profileVersion（避免逐条查 index_profile 的 N+1）。
        Map<Long, Integer> profileVersions = profileVersionsOf(
                builds.stream().map(IndexBuild::getIndexProfileId).filter(Objects::nonNull).toList());
        // ③ 映射为构建历史视图。
        return builds.stream()
                .map(build -> toIndexBuildVo(build, profileVersions.get(build.getIndexProfileId())))
                .toList();
    }

    @Override
    @Transactional
    public Task triggerIndexBuild(long kbId, String idempotencyKey) {
        // ① 校验库归属；仅 ACTIVE 库可构建索引（归档/删除中的库内容冻结）。
        Kb kb = requireKb(kbId);
        long tenantId = effectiveTenantId(kb);
        if (!STATUS_ACTIVE.equals(kb.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库当前状态不可构建索引");
        }
        // ② 构建序号：同 (租户, 库, profile) 内自增（uq_index_build_no 兜底并发）。
        IndexBuild latest = indexBuildMapper.selectOne(new LambdaQueryWrapper<IndexBuild>()
                .eq(IndexBuild::getTenantId, tenantId)
                .eq(IndexBuild::getKbId, kbId)
                .eq(IndexBuild::getIndexProfileId, kb.getIndexProfileId())
                .orderByDesc(IndexBuild::getBuildNo)
                .last("LIMIT 1"));
        int buildNo = (latest == null || latest.getBuildNo() == null) ? 1 : latest.getBuildNo() + 1;
        // ③ 幂等键：客户端未携带则自动生成（uq_index_build_idempotency 防重复提交）。
        String idempotency = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : "kb-build-" + kbId + "-" + UUID.randomUUID();
        // ④ 构建输入规模：当前库内未删文档数（构建 worker 以此为输入集；跨模块经 DocumentService 计数）。
        long documentCount = documentServiceProvider.getObject()
                .listDocuments(kbId, null, null, null, null, null, 1, 1).total();
        // ⑤ 登记构建记录：QUEUED 起步；物理名全局唯一（uq_index_build_name），读别名按库固定。
        IndexBuild build = new IndexBuild();
        build.setTenantId(tenantId);
        build.setKbId(kbId);
        build.setIndexProfileId(kb.getIndexProfileId());
        build.setBuildNo(buildNo);
        build.setPhysicalName("kb-" + kbId + "-b" + buildNo + "-" + UUID.randomUUID().toString().substring(0, 8));
        build.setReadAlias("kb-" + kbId + "-read");
        build.setStatus(BUILD_STATUS_QUEUED);
        build.setIdempotencyKey(idempotency);
        build.setDocumentCount(documentCount);
        build.setQueuedAt(java.time.Instant.now());
        try {
            indexBuildMapper.insert(build);
        } catch (DuplicateKeyException e) {
            // 撞幂等唯一约束：同一 Idempotency-Key 的重复提交按冲突拒绝。
            throw new ApiException(ErrorCode.CONFLICT, "索引构建任务重复提交");
        }
        // TODO(fail-closed)：rag-engine 当前仅有单文档摄取/删除端点，缺「按库重建索引」能力
        //  （RagEnginePort 未提供该端口）；本实现只登记 index_build=QUEUED 构建记录，
        //  真实构建由后续索引 worker / rag-engine 新端点推进，在此之前记录停留 QUEUED、不假报进度
        //  （与 IngestionUseCaseImpl 的 fail-closed 约定一致）。
        return taskService.submit("INDEX_BUILD", BUILD_STATUS_QUEUED,
                "「" + kb.getName() + "」索引构建已排队", 0,
                "INDEX_BUILD", String.valueOf(build.getId()), "等待索引构建 worker 执行");
    }

    @Override
    public IndexBuildVo getIndexBuild(long buildId) {
        // ① 按主键加载构建记录。
        IndexBuild build = indexBuildMapper.selectById(buildId);
        if (build == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "索引构建任务不存在");
        }
        // ② 跨租户访问按「不存在」处理（不泄露其他租户资源的存在性）。
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && build.getTenantId() != null && build.getTenantId() > 0
                && !currentTenantId.equals(build.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "索引构建任务不存在");
        }
        // ③ 回填 profileVersion 后输出详情视图。
        Map<Long, Integer> profileVersions = profileVersionsOf(List.of(build.getIndexProfileId()));
        return toIndexBuildVo(build, profileVersions.get(build.getIndexProfileId()));
    }

    // =====================================================================
    // 内部工具：校验 / 聚合 / 异步清理
    // =====================================================================

    /** 加载知识库并做租户归属校验（所有按 kbId 操作的统一入口，deny-by-default）。 */
    private Kb requireKb(long kbId) {
        Kb kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        // 已认证租户与库归属租户不一致 → 拒绝；tenantId=0/null 的历史数据跳过（见 kbBrief 修复约定）。
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && kb.getTenantId() != null && kb.getTenantId() > 0
                && !currentTenantId.equals(kb.getTenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权访问该知识库");
        }
        return kb;
    }

    /** 子表操作使用的有效租户：历史数据 tenant_id 为 0/null 时兜底默认租户 1（与 kbBrief 一致）。 */
    private long effectiveTenantId(Kb kb) {
        return (kb.getTenantId() == null || kb.getTenantId() <= 0) ? DEFAULT_TENANT_ID : kb.getTenantId();
    }

    /** 当前用户在库内的角色（OWNER/EDITOR/VIEWER）；未认证或非成员返回 null（前端据此控制按钮显隐）。 */
    private String currentRole(long tenantId, long kbId) {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            return null;
        }
        KbMember member = kbMemberMapper.selectOne(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId)
                .last("LIMIT 1"));
        return member != null ? member.getRole() : null;
    }

    /** 批量解析租户内用户显示名；identity 服务未装配（db 关闭）时返回空映射（展示名降级为空串）。 */
    private Map<Long, String> userNames(long tenantId, List<Long> userIds) {
        UserAccountService userAccountService = userAccountServiceIfAvailable();
        if (userAccountService == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountService.displayNamesOf(tenantId, userIds);
    }

    /** 懒获取 identity 模块服务；db.enabled=false 时无实现 bean，返回 null 由调用方降级。 */
    private UserAccountService userAccountServiceIfAvailable() {
        return userAccountServiceProvider.getIfAvailable();
    }

    /** 统计库内 OWNER 数量（最后一名 OWNER 保护守卫用）。 */
    private long countOwners(long tenantId, long kbId) {
        Long count = kbMemberMapper.selectCount(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getRole, KB_ROLE_OWNER));
        return count == null ? 0 : count;
    }

    /** 克隆名称去重：优先原名，冲突追加（副本2）（副本3）…（上限 99 次后要求显式命名）。 */
    private String uniqueCloneName(Long tenantId, String desiredName) {
        // 名称列宽 varchar(128)：预留后缀空间先截断基名，避免拼接后超长落库失败。
        String baseName = desiredName.length() > 112 ? desiredName.substring(0, 112) : desiredName;
        String candidate = baseName;
        for (int sequence = 2; sequence <= 99; sequence++) {
            Long sameNameCount = kbMapper.selectCount(new LambdaQueryWrapper<Kb>()
                    .eq(Kb::getTenantId, tenantId)
                    .eq(Kb::getName, candidate));
            if (sameNameCount == null || sameNameCount == 0) {
                return candidate;
            }
            // 已被占用：追加序号重试（「X（副本2）」「X（副本3）」…）。
            candidate = baseName + "（副本" + sequence + "）";
        }
        throw new ApiException(ErrorCode.CONFLICT, "副本名称冲突次数过多，请指定新名称");
    }

    /** 详情视图：聚合成员/当前用户角色/文档计数/索引配置名（变更类端点返回完整可回显数据）。 */
    private KbVo toDetailVo(Kb kb) {
        long tenantId = effectiveTenantId(kb);
        // 成员 + 显示名一次聚合（joinedAt=入伙时间）。
        List<KbMember> members = kbMemberMapper.selectList(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getTenantId, tenantId)
                .eq(KbMember::getKbId, kb.getId())
                .orderByAsc(KbMember::getId));
        Map<Long, String> namesByUser = userNames(tenantId,
                members.stream().map(KbMember::getUserId).toList());
        List<KbMemberVo> memberVos = members.stream()
                .map(member -> new KbMemberVo(member.getUserId(),
                        namesByUser.getOrDefault(member.getUserId(), ""),
                        member.getRole(), member.getCreateTime()))
                .toList();
        // 文档计数经 document 模块服务聚合（跨模块仅经 Service；size=1 只为取 total）。
        long documentCount = documentServiceProvider.getObject()
                .listDocuments(kb.getId(), null, null, null, null, null, 1, 1).total();
        // 索引配置名（详情页展示；配置缺失时置空）。
        IndexProfile profile = kb.getIndexProfileId() == null
                ? null
                : indexProfileMapper.selectById(kb.getIndexProfileId());
        // chunkCount 聚合依赖 indexing 模块（chunk_meta），该模块尚未提供查询端口，当前如实置 0。
        return new KbVo(
                kb.getId(),
                kb.getName(),
                kb.getDescription(),
                kb.getVisibility(),
                kb.getStatus(),
                currentRole(tenantId, kb.getId()),
                documentCount,
                0L,
                kb.getDataRegion(),
                profile != null ? profile.getName() : null,
                Boolean.TRUE.equals(kb.getRequiresReview()),
                Boolean.TRUE.equals(kb.getOcrEnabled()),
                kb.getCreateTime(),
                kb.getUpdateTime(),
                memberVos);
    }

    /** 批量查询索引配置版本（profileId → profileVersion；未命中配置不进入结果）。 */
    private Map<Long, Integer> profileVersionsOf(List<Long> profileIds) {
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> versions = new HashMap<>();
        for (IndexProfile profile : indexProfileMapper.selectBatchIds(profileIds)) {
            // 同一 profile 多行引用时取首次结果即可（配置不可变）。
            versions.putIfAbsent(profile.getId(), profile.getProfileVersion());
        }
        return versions;
    }

    /** 构建记录 → 构建历史视图（quality_report JSONB 字符串解析为 qualityGate 对象）。 */
    private IndexBuildVo toIndexBuildVo(IndexBuild build, Integer profileVersion) {
        return new IndexBuildVo(
                build.getId(),
                profileVersion != null ? profileVersion : 0,
                build.getStatus(),
                build.getDocumentCount() != null ? build.getDocumentCount() : 0L,
                build.getChunkCount() != null ? build.getChunkCount() : 0L,
                build.getFailedCount() != null ? build.getFailedCount() : 0L,
                parseQualityReport(build.getQualityReport()),
                build.getErrorCode(),
                build.getCreateTime(),
                build.getPublishedAt());
    }

    /** quality_report（JSONB→String）解析为对象；空值返回 null，历史脏值原样返回不阻断列表。 */
    private Object parseQualityReport(String qualityReport) {
        if (!StringUtils.hasText(qualityReport)) {
            return null;
        }
        try {
            return objectMapper.readValue(qualityReport, Object.class);
        } catch (Exception e) {
            // 解析失败按原始字符串透出（数据问题不阻断查询链路）。
            return qualityReport;
        }
    }

    /**
     * 向量清理注册：事务活跃时挂到提交后回调（避免主事务回滚后向量已被物理清除），
     * 无事务上下文则立即投递（与 @Transactional 方法外调用的兜底路径）。
     */
    private void registerVectorCleanupAfterCommit(TenantId tenantId, long kbId, List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            // 无文档即无向量，直接短路。
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 提交成功后才触发清理（删除向量不可逆，不能先于事务结果执行）。
                    submitVectorCleanup(tenantId, kbId, documentIds);
                }
            });
        } else {
            submitVectorCleanup(tenantId, kbId, documentIds);
        }
    }

    /** 向量清理任务投递到单线程执行器：逐文档调用 rag-engine 删除接口，单篇失败不中断整体。 */
    private void submitVectorCleanup(TenantId tenantId, long kbId, List<Long> documentIds) {
        vectorCleanupExecutor.execute(() -> {
            int failed = 0;
            for (Long documentId : documentIds) {
                try {
                    // versionNo=null 表示删除该文档全部版本的向量（rag-engine ingest/delete 语义）。
                    ragEnginePort.deleteVectors(tenantId, documentId, null);
                } catch (Exception e) {
                    failed++;
                    // TODO(fail-closed)：rag-engine 缺「按库批量删向量」端点，当前逐文档调用；
                    //  单篇失败仅告警不重试，残留向量待后续 deletion_task 补偿任务回收。
                    log.warn("知识库删除向量清理失败 tenantId={} kbId={} documentId={}",
                            tenantId.value(), kbId, documentId, e);
                }
            }
            if (failed > 0) {
                // 汇总失败规模（运维对账用；不向调用方抛错——主流程已受理）。
                log.error("知识库向量清理未全部完成 tenantId={} kbId={} failed={}/{}",
                        tenantId.value(), kbId, failed, documentIds.size());
            }
        });
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
