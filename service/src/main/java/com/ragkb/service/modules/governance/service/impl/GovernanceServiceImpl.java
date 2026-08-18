package com.ragkb.service.modules.governance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.security.SecurityUtils;
import com.ragkb.service.modules.document.service.DocumentService;
import com.ragkb.service.modules.governance.dto.LegalHoldDto;
import com.ragkb.service.modules.governance.dto.MetadataSchemaDto;
import com.ragkb.service.modules.governance.dto.RetentionPolicyDto;
import com.ragkb.service.modules.governance.dto.RetentionPolicyToggleDto;
import com.ragkb.service.modules.governance.dto.ReviewActionDto;
import com.ragkb.service.modules.governance.persistence.entity.DeletionReceipt;
import com.ragkb.service.modules.governance.persistence.entity.DeletionTarget;
import com.ragkb.service.modules.governance.persistence.entity.DeletionTask;
import com.ragkb.service.modules.governance.persistence.entity.LegalHold;
import com.ragkb.service.modules.governance.persistence.entity.LegalHoldDocument;
import com.ragkb.service.modules.governance.persistence.entity.MetadataSchema;
import com.ragkb.service.modules.governance.persistence.entity.RetentionPolicy;
import com.ragkb.service.modules.governance.persistence.mapper.DeletionReceiptMapper;
import com.ragkb.service.modules.governance.persistence.mapper.DeletionTargetMapper;
import com.ragkb.service.modules.governance.persistence.mapper.DeletionTaskMapper;
import com.ragkb.service.modules.governance.persistence.mapper.LegalHoldDocumentMapper;
import com.ragkb.service.modules.governance.persistence.mapper.LegalHoldMapper;
import com.ragkb.service.modules.governance.persistence.mapper.MetadataSchemaMapper;
import com.ragkb.service.modules.governance.persistence.mapper.RetentionPolicyMapper;
import com.ragkb.service.modules.governance.service.GovernanceService;
import com.ragkb.service.modules.governance.vo.DeletionProgressVo;
import com.ragkb.service.modules.governance.vo.DeletionReceiptVo;
import com.ragkb.service.modules.governance.vo.DeletionTaskVo;
import com.ragkb.service.modules.governance.vo.LegalHoldVo;
import com.ragkb.service.modules.governance.vo.MetadataFieldVo;
import com.ragkb.service.modules.governance.vo.MetadataSchemaVo;
import com.ragkb.service.modules.governance.vo.RetentionPolicyVo;
import com.ragkb.service.modules.governance.vo.ReviewItemVo;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.identity.service.UserAccountService;
import com.ragkb.service.modules.knowledge.service.KbService;
import com.ragkb.service.modules.rag.port.RagEnginePort;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 治理中心用例实现：元数据 schema / 内容审核 / 保留策略与法律保全 / 删除审批与删除证明。
 *
 * <p>业务规则来源：{@code deploy/ddl/init.sql} 的 CHECK 约束（存储层权威枚举）、
 * {@code web/api-client/types/governance.ts} 的前端契约（API 层权威枚举），
 * 两套枚举不一致处（如 schema 的 ACTIVE↔PUBLISHED、保留策略的 disposition↔action）
 * 由本服务在出入参边界做一一映射，数据库只落 DDL 枚举。
 *
 * <p>多租户隔离（deny-by-default）：所有查询带 tenant_id + del_flag=0（@TableLogic 自动过滤），
 * 按资源 id 的操作先经 requireXxx 校验「资源存在 + 当前认证租户与资源归属租户一致」，
 * 跨租户访问统一按不存在/无权拒绝，不泄露其他租户资源的存在性。
 *
 * <p>跨模块协作只经 Service/Port（PackageStructureTest 约束）：
 * document 模块经 {@link DocumentService}（审核状态流转/治理快照/批量软删），
 * knowledge 模块经 {@link KbService}（库名回填），identity 模块经
 * {@link UserAccountService}（用户显示名），rag-engine 经 {@link RagEnginePort}（向量清理）。
 * 其中 DocumentService/KbService/UserAccountService 用 {@link ObjectProvider} 懒获取，
 * 与其他条件装配 Bean 的循环依赖及缺 Bean 场景解耦。
 */
// 装配条件：本实现依赖 MyBatis Mapper（仅 ragkb.db.enabled=true 时注册）；
// scaffold 模式（无数据库）由 GovernanceServiceScaffoldImpl 提供同接口的 501 桩，
// 4 个 Controller 无条件装配，两种模式下均能注入唯一实现。
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class GovernanceServiceImpl implements GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceServiceImpl.class);

    // ---------- 存储层枚举常量（与 DDL CHECK 约束一一对应，禁止裸写魔法值扩散） ----------

    /** metadata_schema.status：草稿（可编辑/可发布）。 */
    private static final String SCHEMA_DRAFT = "DRAFT";
    /** metadata_schema.status：已发布（API 契约的 PUBLISHED；发布后不可变，只能建新版本）。 */
    private static final String SCHEMA_ACTIVE = "ACTIVE";
    /** metadata_schema.status：已退役（被同名更高版本取代的旧发布版本）。 */
    private static final String SCHEMA_RETIRED = "RETIRED";

    /** retention_policy.status：启用。 */
    private static final String POLICY_ACTIVE = "ACTIVE";
    /** retention_policy.status：停用。 */
    private static final String POLICY_DISABLED = "DISABLED";
    /** 契约 appliesTo（TENANT/KB/CATEGORY）→ 存储 scope_type 的映射。 */
    private static final Map<String, String> APPLIES_TO_SCOPE = Map.of(
            "TENANT", "TENANT", "KB", "KB", "CATEGORY", "CLASSIFICATION");
    /** 存储 scope_type → 契约 appliesTo 的反向映射。 */
    private static final Map<String, String> SCOPE_TO_APPLIES = Map.of(
            "TENANT", "TENANT", "KB", "KB", "CLASSIFICATION", "CATEGORY");
    /** 契约 action（AUTO_EXPIRE/REVIEW/RETAIN）→ 存储 disposition 的映射。 */
    private static final Map<String, String> ACTION_DISPOSITION = Map.of(
            "AUTO_EXPIRE", "DELETE", "REVIEW", "REVIEW", "RETAIN", "ARCHIVE");
    /** 存储 disposition → 契约 action 的反向映射。 */
    private static final Map<String, String> DISPOSITION_TO_ACTION = Map.of(
            "DELETE", "AUTO_EXPIRE", "REVIEW", "REVIEW", "ARCHIVE", "RETAIN");
    /** 契约保留时长单位为月，存储列为天：按 30 天/月近似换算（业务约定的粗粒度保留期）。 */
    private static final int DAYS_PER_MONTH = 30;

    /** legal_hold.status：保全中（此状态下文档禁止物理删除）。 */
    private static final String HOLD_ACTIVE = "ACTIVE";
    /** legal_hold.status：已解除（恢复可删除）。 */
    private static final String HOLD_RELEASED = "RELEASED";

    /** deletion_task.status：待审批。 */
    private static final String TASK_QUEUED = "QUEUED";
    /** deletion_task.status：执行中。 */
    private static final String TASK_RUNNING = "RUNNING";
    /** deletion_task.status：被阻断（如法律保全冲突，条件解除后可重试）。 */
    private static final String TASK_BLOCKED = "BLOCKED";
    /** deletion_task.status：全部目标处置成功（终态）。 */
    private static final String TASK_SUCCEEDED = "SUCCEEDED";
    /** deletion_task.status：部分成功（终态，需人工复核失败目标）。 */
    private static final String TASK_PARTIAL = "PARTIAL";
    /** deletion_task.status：全部失败（终态）。 */
    private static final String TASK_FAILED = "FAILED";
    /** deletion_task.status：已取消（终态）。 */
    private static final String TASK_CANCELLED = "CANCELLED";
    /** deletion_task 终态集合（不可再审批重试）。 */
    private static final Set<String> TASK_TERMINAL_STATUSES = Set.of(TASK_SUCCEEDED, TASK_PARTIAL, TASK_FAILED, TASK_CANCELLED);
    /** 删除审批当前仅支持文档级任务（ck_deletion_task_resource 的 DOCUMENT 取值）。 */
    private static final String RESOURCE_DOCUMENT = "DOCUMENT";

    /** deletion_target.target_type：存储/元数据层（document 行软删 + 对象引用摘除）。 */
    private static final String TARGET_OBJECT = "OBJECT";
    /** deletion_target.target_type：检索索引层（rag-engine 向量删除）。 */
    private static final String TARGET_SEARCH_INDEX = "SEARCH_INDEX";
    /** deletion_target.status：待处置。 */
    private static final String TARGET_PENDING = "PENDING";
    /** deletion_target.status：处置成功。 */
    private static final String TARGET_SUCCEEDED = "SUCCEEDED";
    /** deletion_target.status：处置失败（last_error_code 记录原因）。 */
    private static final String TARGET_FAILED = "FAILED";
    /** deletion_target.status：因法律保全保留（未处置，解除后可重试）。 */
    private static final String TARGET_RETAINED = "RETAINED";
    /** deletion_target.status：跳过（目标已被并发处置/不存在，幂等收口）。 */
    private static final String TARGET_SKIPPED = "SKIPPED";

    /** deletion_task.blocked_reason_code：目标文档处于法律保全中。 */
    private static final String BLOCKED_REASON_LEGAL_HOLD = "LEGAL_HOLD_ACTIVE";

    /** 历史数据 tenant_id 为 0/null（未归属租户）时兜底使用的默认租户（与 KbServiceImpl 约定一致）。 */
    private static final long DEFAULT_TENANT_ID = 1L;

    // ---------- 依赖（本模块持久化 + 跨模块 Service/Port） ----------

    /** 元数据 schema 表（metadata_schema）。 */
    @Autowired
    private MetadataSchemaMapper metadataSchemaMapper;

    /** 保留策略表（retention_policy）。 */
    @Autowired
    private RetentionPolicyMapper retentionPolicyMapper;

    /** 法律保全表（legal_hold）。 */
    @Autowired
    private LegalHoldMapper legalHoldMapper;

    /** 保全-文档关联表（legal_hold_document）。 */
    @Autowired
    private LegalHoldDocumentMapper legalHoldDocumentMapper;

    /** 删除任务表（deletion_task）。 */
    @Autowired
    private DeletionTaskMapper deletionTaskMapper;

    /** 删除目标表（deletion_target，按存储层逐个推进）。 */
    @Autowired
    private DeletionTargetMapper deletionTargetMapper;

    /** 删除证明表（deletion_receipt，任务完成后一次性生成）。 */
    @Autowired
    private DeletionReceiptMapper deletionReceiptMapper;

    /** 文档模块服务（懒获取：审核状态流转/治理快照/批量软删经此触达，不直连 document 持久化层）。 */
    @Autowired
    private ObjectProvider<DocumentService> documentServiceProvider;

    /** 知识库模块服务（懒获取：审核队列与删除任务的 kbName 回填）。 */
    @Autowired
    private ObjectProvider<KbService> kbServiceProvider;

    /** 身份模块服务（懒获取：提交人/操作人/保全创建人的显示名回填，缺 Bean 时降级空串）。 */
    @Autowired
    private ObjectProvider<UserAccountService> userAccountServiceProvider;

    /** rag-engine 端口：删除审批通过后按文档清理向量。 */
    @Autowired
    private RagEnginePort ragEnginePort;

    /** schema_json / preview_json / summary_json（JSONB→String）的序列化与解析器。 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 删除审批收尾单线程执行器：向量清理与任务终态推进属旁路任务，
     * 不占用审批请求线程（守护线程模式对齐 KbServiceImpl 的清理线程约定）。
     */
    private final ExecutorService deletionFinalizerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "governance-deletion-finalizer");
        thread.setDaemon(true);
        return thread;
    });

    /** 应用停机时拒绝新收尾任务并尽量排空已提交任务（未完成的留待补偿核对）。 */
    @PreDestroy
    public void shutdownDeletionFinalizer() {
        deletionFinalizerExecutor.shutdown();
    }

    // =====================================================================
    // 元数据 schema：DRAFT →（发布）→ ACTIVE(=PUBLISHED)；新版本发布时旧 ACTIVE 版本退役
    // =====================================================================

    @Override
    public List<MetadataSchemaVo> listMetadataSchemas() {
        // 列表查询：租户过滤（未认证 dev 场景不过滤，与 listDocuments 约定一致），名称+版本稳定排序。
        Long tenantId = currentTenantIdOrNull();
        List<MetadataSchema> schemas = metadataSchemaMapper.selectList(new LambdaQueryWrapper<MetadataSchema>()
                .eq(tenantId != null, MetadataSchema::getTenantId, tenantId)
                .orderByAsc(MetadataSchema::getName)
                .orderByAsc(MetadataSchema::getSchemaVersion));
        // 解析 schema_json → 字段列表（解析失败的脏数据不阻断列表，字段降级为空）。
        return schemas.stream().map(this::toSchemaVo).toList();
    }

    @Override
    @Transactional
    public MetadataSchemaVo createMetadataSchema(MetadataSchemaDto request, String idempotencyKey) {
        long tenantId = effectiveTenantId();
        // 版本链：同 (租户, 名称) 下已有版本则新版本号 = 最大版本 + 1，否则 v1 起步
        //（kb 维度契约未提供，kb_id 落 NULL 表示全局 schema）。
        MetadataSchema latest = metadataSchemaMapper.selectOne(new LambdaQueryWrapper<MetadataSchema>()
                .eq(MetadataSchema::getTenantId, tenantId)
                .eq(MetadataSchema::getName, request.name().trim())
                .orderByDesc(MetadataSchema::getSchemaVersion)
                .last("LIMIT 1"));
        int nextVersion = latest == null || latest.getSchemaVersion() == null
                ? 1
                : latest.getSchemaVersion() + 1;
        // schema_json 组装：description 与 fields 一并落 JSONB（实体无独立 description 列）。
        MetadataSchema schema = new MetadataSchema();
        schema.setTenantId(tenantId);
        schema.setName(request.name().trim());
        schema.setSchemaVersion(nextVersion);
        schema.setSchemaJson(writeSchemaJson(request.description(), request.fields()));
        // 新建一律 DRAFT：已发布（ACTIVE）的旧版本不受影响，直至新版本显式发布。
        schema.setStatus(SCHEMA_DRAFT);
        try {
            metadataSchemaMapper.insert(schema);
        } catch (DuplicateKeyException e) {
            // 并发创建撞 uq_metadata_schema_version：提示刷新重试（预检窗口外的兜底）。
            throw new ApiException(ErrorCode.CONFLICT, "同名 schema 版本已存在，请刷新后重试");
        }
        return toSchemaVo(schema);
    }

    @Override
    @Transactional
    public MetadataSchemaVo publishMetadataSchema(long schemaId, String idempotencyKey) {
        // ① 加载并校验 schema 归属（存在性 + 租户，deny-by-default）。
        MetadataSchema schema = requireSchema(schemaId);
        // 幂等重放：已发布版本直接返回现状，不产生多余写。
        if (SCHEMA_ACTIVE.equals(schema.getStatus())) {
            return toSchemaVo(schema);
        }
        // 只有草稿可发布：退役版本是被取代的历史版本，不允许再次激活。
        if (!SCHEMA_DRAFT.equals(schema.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "该 schema 版本已退役，不能再次发布");
        }
        // ② DRAFT → ACTIVE 并落发布时间（发布后不可变：本模块无更新端点，编辑只能建新版本）。
        schema.setStatus(SCHEMA_ACTIVE);
        schema.setPublishedAt(Instant.now());
        metadataSchemaMapper.updateById(schema);
        // ③ 新版本取代旧版本：同 (租户, 名称) 的其他 ACTIVE 版本统一置 RETIRED。
        metadataSchemaMapper.update(null, new LambdaUpdateWrapper<MetadataSchema>()
                .eq(MetadataSchema::getTenantId, schema.getTenantId())
                .eq(MetadataSchema::getName, schema.getName())
                .ne(MetadataSchema::getId, schemaId)
                .eq(MetadataSchema::getStatus, SCHEMA_ACTIVE)
                .set(MetadataSchema::getStatus, SCHEMA_RETIRED));
        return toSchemaVo(schema);
    }

    // =====================================================================
    // 内容审核：队列查询与审批动作（文档状态流转经 DocumentService，本模块只做编排）
    // =====================================================================

    @Override
    public PageData<ReviewItemVo> listReviews(int page, int size) {
        // 待审队列数据（文档 + 最近送审留痕）来自 document 模块，本模块补齐展示字段。
        PageData<DocumentService.ReviewQueueItem> queue =
                documentServiceProvider.getObject().listPendingReviews(page, size);
        if (queue.items().isEmpty()) {
            return PageData.empty(page, size);
        }
        // 批量回填库名（跨模块经 KbService，一次查询避免 N+1）。
        List<Long> kbIds = queue.items().stream().map(DocumentService.ReviewQueueItem::kbId).distinct().toList();
        Map<Long, String> kbNames = kbServiceProvider.getObject().kbNamesByIds(kbIds);
        // 批量回填提交人显示名（跨模块经 UserAccountService；未命中用户置空串展示）。
        List<Long> submitterIds = queue.items().stream()
                .map(DocumentService.ReviewQueueItem::submitterId)
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> submitterNames = userNames(submitterIds);
        return PageData.of(queue.items().stream()
                .map(item -> new ReviewItemVo(
                        item.documentId(),
                        item.title(),
                        kbNames.getOrDefault(item.kbId(), ""),
                        item.submitterId() != null
                                ? submitterNames.getOrDefault(item.submitterId(), "")
                                : "",
                        item.sensitivity(),
                        // 提交时间统一 ISO-8601（前端 formatRelative 解析）。
                        item.submittedAt() != null ? item.submittedAt().toString() : "",
                        item.commentCount()))
                .toList(), queue.total(), page, size);
    }

    @Override
    public void approveReview(long reviewId, ReviewActionDto request, String idempotencyKey) {
        // 契约说明：前端契约（web/api-client）以 documentId 作为审批端点路径参数，
        // 故此处的 reviewId 实为 documentId —— 审核以文档为粒度，最近一条 SUBMIT 留痕即当前轮送审。
        documentServiceProvider.getObject().approveDocumentReview(reviewId, commentOf(request));
    }

    @Override
    public void rejectReview(long reviewId, ReviewActionDto request, String idempotencyKey) {
        // 驳回：reviewId 实为 documentId（同 approveReview 的契约说明）。
        documentServiceProvider.getObject().rejectDocumentReview(reviewId, commentOf(request));
    }

    @Override
    public void withdrawDocument(long documentId, String idempotencyKey) {
        // 撤回待审文档：文档侧状态机守卫（仅 PENDING_REVIEW 可撤回）经 DocumentService 执行。
        documentServiceProvider.getObject().withdrawDocumentFromReview(documentId);
    }

    // =====================================================================
    // 保留策略：创建/启停（契约的月/天单位与 appliesTo/action 枚举在边界映射）
    // =====================================================================

    @Override
    public List<RetentionPolicyVo> listRetentionPolicies() {
        // 列表查询：租户过滤 + 创建时间倒序（新策略在前）。
        Long tenantId = currentTenantIdOrNull();
        List<RetentionPolicy> policies = retentionPolicyMapper.selectList(
                new LambdaQueryWrapper<RetentionPolicy>()
                        .eq(tenantId != null, RetentionPolicy::getTenantId, tenantId)
                        .orderByDesc(RetentionPolicy::getId));
        return policies.stream().map(this::toPolicyVo).toList();
    }

    @Override
    @Transactional
    public RetentionPolicyVo createRetentionPolicy(RetentionPolicyDto request, String idempotencyKey) {
        // ① 枚举白名单校验（拦截契约枚举外的脏值，对齐 DDL CHECK）。
        String appliesTo = StringUtils.hasText(request.appliesTo()) ? request.appliesTo().trim().toUpperCase() : "";
        if (!APPLIES_TO_SCOPE.containsKey(appliesTo)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的适用范围: " + request.appliesTo());
        }
        String action = StringUtils.hasText(request.action()) ? request.action().trim().toUpperCase() : "";
        if (!ACTION_DISPOSITION.containsKey(action)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的处置动作: " + request.action());
        }
        if (request.durationMonths() == null || request.durationMonths() <= 0) {
            // 保留期必须为正整数月（存储列 ck_retention_days 要求 retention_days > 0）。
            throw new ApiException(ErrorCode.BAD_REQUEST, "保留时长必须为正整数月");
        }
        long tenantId = effectiveTenantId();
        String name = request.name().trim();
        // ② 租户内同名预检（uq_retention_policy_name 兜底并发窗口）。
        Long sameNameCount = retentionPolicyMapper.selectCount(new LambdaQueryWrapper<RetentionPolicy>()
                .eq(RetentionPolicy::getTenantId, tenantId)
                .eq(RetentionPolicy::getName, name));
        if (sameNameCount != null && sameNameCount > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "同名保留策略已存在: " + name);
        }
        // ③ 落库：契约单位月 → 存储单位天（30 天/月近似）；scope_key 暂统一留空
        //（KB 级策略的目标库 id 待契约 RetentionPolicyInput 补 targetId 字段后接入，见模块文档遗留项）。
        RetentionPolicy policy = new RetentionPolicy();
        policy.setTenantId(tenantId);
        policy.setName(name);
        policy.setScopeType(APPLIES_TO_SCOPE.get(appliesTo));
        policy.setScopeKey(null);
        policy.setRetentionDays(request.durationMonths() * DAYS_PER_MONTH);
        policy.setDisposition(ACTION_DISPOSITION.get(action));
        // 新建默认启用（status=ACTIVE，对齐 DDL DEFAULT）。
        policy.setStatus(POLICY_ACTIVE);
        try {
            retentionPolicyMapper.insert(policy);
        } catch (DuplicateKeyException e) {
            // 并发创建撞同名唯一约束：按冲突拒绝（预检窗口外的兜底）。
            throw new ApiException(ErrorCode.CONFLICT, "同名保留策略已存在，请刷新后重试");
        }
        return toPolicyVo(policy);
    }

    @Override
    @Transactional
    public RetentionPolicyVo toggleRetentionPolicy(long policyId, RetentionPolicyToggleDto request) {
        // ① 加载并校验策略归属（deny-by-default）。
        RetentionPolicy policy = requirePolicy(policyId);
        String targetStatus = Boolean.TRUE.equals(request.enabled()) ? POLICY_ACTIVE : POLICY_DISABLED;
        // 幂等：目标状态与现状一致时直接返回，不产生多余写。
        if (targetStatus.equals(policy.getStatus())) {
            return toPolicyVo(policy);
        }
        // ② 启停流转（ACTIVE ↔ DISABLED，双向可逆）；实体带 row_version 走乐观锁更新。
        policy.setStatus(targetStatus);
        if (retentionPolicyMapper.updateById(policy) == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "保留策略已被他人修改，请刷新后重试");
        }
        return toPolicyVo(policy);
    }

    // =====================================================================
    // 法律保全：创建选定文档范围 → 保全中禁止物理删除 → 解除后恢复
    // =====================================================================

    @Override
    public List<LegalHoldVo> listLegalHolds() {
        // 列表查询：租户过滤 + 创建时间倒序（新保全在前）。
        Long tenantId = currentTenantIdOrNull();
        List<LegalHold> holds = legalHoldMapper.selectList(new LambdaQueryWrapper<LegalHold>()
                .eq(tenantId != null, LegalHold::getTenantId, tenantId)
                .orderByDesc(LegalHold::getId));
        if (holds.isEmpty()) {
            return List.of();
        }
        // 批量回填各保全覆盖的文档清单（关联表一次 in 查询后按保全分组）。
        List<Long> holdIds = holds.stream().map(LegalHold::getId).toList();
        Map<Long, List<Long>> documentIdsByHold = legalHoldDocumentMapper.selectList(
                        new LambdaQueryWrapper<LegalHoldDocument>()
                                .eq(tenantId != null, LegalHoldDocument::getTenantId, tenantId)
                                .in(LegalHoldDocument::getLegalHoldId, holdIds))
                .stream()
                .collect(Collectors.groupingBy(LegalHoldDocument::getLegalHoldId,
                        Collectors.mapping(LegalHoldDocument::getDocumentId, Collectors.toList())));
        // 批量回填创建人显示名（create_by 自动填充的 userId → 姓名）。
        Map<Long, String> creatorNames = userNames(holds.stream()
                .map(LegalHold::getCreateBy).filter(Objects::nonNull).distinct().toList());
        return holds.stream()
                .map(hold -> new LegalHoldVo(
                        hold.getId(),
                        hold.getName(),
                        documentIdsByHold.getOrDefault(hold.getId(), List.of()),
                        hold.getReason(),
                        hold.getCreateBy() != null ? creatorNames.getOrDefault(hold.getCreateBy(), "") : "",
                        hold.getCreateTime(),
                        hold.getReleasedAt()))
                .toList();
    }

    @Override
    @Transactional
    public LegalHoldVo createLegalHold(LegalHoldDto request, String idempotencyKey) {
        long tenantId = effectiveTenantId();
        // ① 文档范围校验：逐个确认文档存在、未删且属当前租户（跨租户按不存在拒绝，deny-by-default）。
        Set<Long> documentIds = new LinkedHashSet<>(request.documentIds());
        for (Long documentId : documentIds) {
            DocumentService.DocumentGovernanceBrief brief =
                    documentServiceProvider.getObject().documentGovernanceBrief(documentId);
            if (brief.tenantId() != tenantId) {
                // 双保险：documentGovernanceBrief 已校验 JWT 租户，此处再核对落库租户一致性。
                throw new ApiException(ErrorCode.NOT_FOUND, "文档不存在: " + documentId);
            }
        }
        // ② 保全主记录：ACTIVE 状态（此状态下文档禁止物理删除）。
        LegalHold hold = new LegalHold();
        hold.setTenantId(tenantId);
        hold.setName(request.name().trim());
        hold.setReason(request.reason());
        hold.setStatus(HOLD_ACTIVE);
        try {
            legalHoldMapper.insert(hold);
        } catch (DuplicateKeyException e) {
            // 撞 uq_legal_hold_name：租户内保全名称唯一。
            throw new ApiException(ErrorCode.CONFLICT, "同名法律保全已存在: " + request.name());
        }
        // ③ 保全-文档关联：批量落 legal_hold_document（uq (tenant,hold,document) 防重复挂载）。
        for (Long documentId : documentIds) {
            LegalHoldDocument link = new LegalHoldDocument();
            link.setTenantId(tenantId);
            link.setLegalHoldId(hold.getId());
            link.setDocumentId(documentId);
            legalHoldDocumentMapper.insert(link);
        }
        // 创建人显示名回填（create_by 由审计填充器写入）。
        Map<Long, String> creatorNames = userNames(hold.getCreateBy() != null
                ? List.of(hold.getCreateBy()) : List.of());
        return new LegalHoldVo(
                hold.getId(),
                hold.getName(),
                List.copyOf(documentIds),
                hold.getReason(),
                hold.getCreateBy() != null ? creatorNames.getOrDefault(hold.getCreateBy(), "") : "",
                hold.getCreateTime(),
                null);
    }

    @Override
    @Transactional
    public LegalHoldVo releaseLegalHold(long holdId) {
        // ① 加载并校验保全归属（deny-by-default）。
        LegalHold hold = requireHold(holdId);
        // 幂等：已解除的保全直接返回现状（ck_legal_hold_release 要求 RELEASED 必须带 released_at）。
        if (HOLD_RELEASED.equals(hold.getStatus())) {
            return toHoldVo(hold, documentIdsOf(hold));
        }
        // ② 解除流转：ACTIVE → RELEASED，记录解除人与解除时间；关联文档随即可再进入删除审批。
        hold.setStatus(HOLD_RELEASED);
        hold.setReleasedBy(SecurityUtils.currentUserId());
        hold.setReleasedAt(Instant.now());
        legalHoldMapper.updateById(hold);
        return toHoldVo(hold, documentIdsOf(hold));
    }

    // =====================================================================
    // 删除审批与删除证明：legal hold 冲突校验 → 按 deletion_target 逐层处置 → 生成 receipt
    // =====================================================================

    @Override
    public List<DeletionTaskVo> listDeletionTasks() {
        // 列表查询：租户过滤 + 创建时间倒序（新任务在前）。
        Long tenantId = currentTenantIdOrNull();
        List<DeletionTask> tasks = deletionTaskMapper.selectList(new LambdaQueryWrapper<DeletionTask>()
                .eq(tenantId != null, DeletionTask::getTenantId, tenantId)
                .orderByDesc(DeletionTask::getId));
        if (tasks.isEmpty()) {
            return List.of();
        }
        // 批量取各任务目标行（进度视图 + 证明留档共用一次 in 查询）。
        List<Long> taskIds = tasks.stream().map(DeletionTask::getId).toList();
        Map<Long, List<DeletionTarget>> targetsByTask = deletionTargetMapper.selectList(
                        new LambdaQueryWrapper<DeletionTarget>()
                                .eq(tenantId != null, DeletionTarget::getTenantId, tenantId)
                                .in(DeletionTarget::getDeletionTaskId, taskIds))
                .stream()
                .collect(Collectors.groupingBy(DeletionTarget::getDeletionTaskId));
        // 批量回填申请人显示名（requested_by → 姓名）。
        Map<Long, String> requesterNames = userNames(tasks.stream()
                .map(DeletionTask::getRequestedBy).filter(Objects::nonNull).distinct().toList());
        return tasks.stream()
                .map(task -> toTaskVo(task,
                        targetsByTask.getOrDefault(task.getId(), List.of()),
                        requesterNames.getOrDefault(task.getRequestedBy() != null ? task.getRequestedBy() : 0L, "")))
                .toList();
    }

    @Override
    @Transactional
    public DeletionTaskVo approveDeletion(long taskId) {
        // ① 加载并校验任务归属（危险操作的租户隔离入口，deny-by-default）。
        DeletionTask task = requireDeletionTask(taskId);
        // 状态机守卫：终态任务不可重复审批（幂等重放场景由前端按列表状态规避）。
        if (TASK_TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "删除任务已终结（" + task.getStatus() + "），不能重复审批");
        }
        if (TASK_RUNNING.equals(task.getStatus())) {
            // 执行中的任务拒绝并发审批（防止双份处置动作）。
            throw new ApiException(ErrorCode.CONFLICT, "删除任务执行中，请勿重复提交");
        }
        // 仅支持文档级删除任务（KB/租户级联删除走 knowledge 模块的 deleteKb 链路）。
        if (!RESOURCE_DOCUMENT.equals(task.getResourceType())) {
            throw new ApiException(ErrorCode.CONFLICT, "仅支持文档级删除任务，当前类型: " + task.getResourceType());
        }
        long documentId = parseDocumentId(task.getResourceId());
        long tenantId = task.getTenantId() != null && task.getTenantId() > 0
                ? task.getTenantId() : DEFAULT_TENANT_ID;
        // ② 读取文档治理快照（软删前的展示/留档数据；不存在或跨租户按 NOT_FOUND 拒绝）。
        DocumentService.DocumentGovernanceBrief brief =
                documentServiceProvider.getObject().documentGovernanceBrief(documentId);
        // ③ legal hold 冲突校验（危险操作红线：保全中的文档禁止物理删除）。
        List<String> activeHoldNames = activeHoldNamesCovering(tenantId, documentId);
        if (!activeHoldNames.isEmpty()) {
            // 冲突处理：任务转 BLOCKED 并落阻断原因，目标行置 RETAINED（保留证据链），整体抛冲突回绝审批。
            task.setStatus(TASK_BLOCKED);
            task.setLegalHoldBlocked(true);
            task.setBlockedReasonCode(BLOCKED_REASON_LEGAL_HOLD);
            task.setPreviewJson(writePreviewJson(brief, "BLOCKED"));
            deletionTaskMapper.updateById(task);
            ensureObjectTarget(task, documentId, TARGET_RETAINED);
            throw new ApiException(ErrorCode.CONFLICT,
                    "文档处于法律保全中（" + String.join("、", activeHoldNames) + "），禁止删除");
        }
        // ④ 通过校验：任务转 RUNNING 并留档快照（软删后 fileName 等展示字段即来自此快照）。
        task.setStatus(TASK_RUNNING);
        task.setLegalHoldBlocked(false);
        task.setBlockedReasonCode(null);
        task.setStartedAt(Instant.now());
        task.setPreviewJson(writePreviewJson(brief, "RUNNING"));
        // 目标行登记：存储/元数据层（OBJECT）+ 检索索引层（SEARCH_INDEX），expected_count=1。
        DeletionTarget objectTarget = ensureObjectTarget(task, documentId, TARGET_PENDING);
        ensureSearchIndexTarget(task, documentId);
        deletionTaskMapper.updateById(task);
        // ⑤ 执行软删（跨模块经 DocumentService；参考 deleteKb 模式但按目标文档逐个处理）。
        List<Long> deleted = documentServiceProvider.getObject()
                .softDeleteDocumentsByIds(tenantId, List.of(documentId));
        Instant now = Instant.now();
        if (deleted.isEmpty()) {
            // 文档已被并发删除/不存在：OBJECT 目标按 SKIPPED 幂等收口（deletion_target 状态机）。
            completeTarget(objectTarget, TARGET_SKIPPED, null);
        } else {
            // OBJECT 目标完成：result_sha256 为该处置动作证据摘要（documentId+时间戳规范化串的 SHA-256）。
            completeTarget(objectTarget, TARGET_SUCCEEDED,
                    sha256Hex("document:" + documentId + ":soft-deleted:" + now));
        }
        // 任务计数推进（completed + failed <= target_count，对齐 ck_deletion_task_counts）。
        deletionTaskMapper.update(null, new LambdaUpdateWrapper<DeletionTask>()
                .eq(DeletionTask::getId, taskId)
                .set(DeletionTask::getCompletedCount, deleted.isEmpty() ? 0 : 1)
                .set(DeletionTask::getFailedCount, 0)
                .set(DeletionTask::getTargetCount, 2));
        // ⑥ 向量清理与任务终态推进走异步旁路（外部 HTTP 不进 DB 事务），注册到事务提交后触发。
        Long operatorId = SecurityUtils.currentUserId();
        registerDeletionFinalizationAfterCommit(tenantId, taskId, documentId, operatorId, now);
        // ⑦ 受理即返回：软删已生效，向量清理与删除证明由后台线程消化，前端轮询任务列表跟进。
        return toTaskVo(deletionTaskMapper.selectById(taskId),
                deletionTargetMapper.selectList(new LambdaQueryWrapper<DeletionTarget>()
                        .eq(DeletionTarget::getDeletionTaskId, taskId)),
                operatorNames(operatorId));
    }

    @Override
    public List<DeletionReceiptVo> listDeletionReceipts() {
        // 列表查询：租户过滤 + 生成时间倒序（新证明在前）。
        Long tenantId = currentTenantIdOrNull();
        List<DeletionReceipt> receipts = deletionReceiptMapper.selectList(
                new LambdaQueryWrapper<DeletionReceipt>()
                        .eq(tenantId != null, DeletionReceipt::getTenantId, tenantId)
                        .orderByDesc(DeletionReceipt::getId));
        if (receipts.isEmpty()) {
            return List.of();
        }
        // 批量取关联任务（documentId/fileName 展示字段来自任务快照 preview_json）。
        List<Long> taskIds = receipts.stream().map(DeletionReceipt::getDeletionTaskId).distinct().toList();
        Map<Long, DeletionTask> tasksById = deletionTaskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(DeletionTask::getId, task -> task, (first, ignored) -> first));
        // 批量回填操作人显示名（summary_json 中留档的 operatorId → 姓名）。
        Map<Long, String> operatorNames = userNames(receipts.stream()
                .map(receipt -> parseOperatorId(receipt.getSummaryJson()))
                .filter(Objects::nonNull).distinct().toList());
        return receipts.stream()
                .map(receipt -> toReceiptVo(receipt, tasksById.get(receipt.getDeletionTaskId()), operatorNames))
                .toList();
    }

    // =====================================================================
    // 内部工具：归属校验 / 视图组装 / JSON 序列化 / 保全冲突查询 / 异步收尾
    // =====================================================================

    /** 加载 schema 并做租户归属校验（deny-by-default：跨租户按不存在处理）。 */
    private MetadataSchema requireSchema(long schemaId) {
        MetadataSchema schema = metadataSchemaMapper.selectById(schemaId);
        if (schema == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "元数据 schema 不存在");
        }
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && schema.getTenantId() != null && schema.getTenantId() > 0
                && !currentTenantId.equals(schema.getTenantId())) {
            // 跨租户访问不泄露资源存在性，统一按不存在拒绝。
            throw new ApiException(ErrorCode.NOT_FOUND, "元数据 schema 不存在");
        }
        return schema;
    }

    /** 加载保留策略并做租户归属校验（deny-by-default）。 */
    private RetentionPolicy requirePolicy(long policyId) {
        RetentionPolicy policy = retentionPolicyMapper.selectById(policyId);
        if (policy == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "保留策略不存在");
        }
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && policy.getTenantId() != null && policy.getTenantId() > 0
                && !currentTenantId.equals(policy.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "保留策略不存在");
        }
        return policy;
    }

    /** 加载法律保全并做租户归属校验（deny-by-default）。 */
    private LegalHold requireHold(long holdId) {
        LegalHold hold = legalHoldMapper.selectById(holdId);
        if (hold == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "法律保全不存在");
        }
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && hold.getTenantId() != null && hold.getTenantId() > 0
                && !currentTenantId.equals(hold.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "法律保全不存在");
        }
        return hold;
    }

    /** 加载删除任务并做租户归属校验（deny-by-default）。 */
    private DeletionTask requireDeletionTask(long taskId) {
        DeletionTask task = deletionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "删除任务不存在");
        }
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && task.getTenantId() != null && task.getTenantId() > 0
                && !currentTenantId.equals(task.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "删除任务不存在");
        }
        return task;
    }

    /** 查询覆盖指定文档的「保全中」legal_hold 名称清单（两段查询替代 join，均为本模块表）。 */
    private List<String> activeHoldNamesCovering(long tenantId, long documentId) {
        // 第一段：文档挂载的全部保全关联（@TableLogic 过滤已删行）。
        List<LegalHoldDocument> links = legalHoldDocumentMapper.selectList(
                new LambdaQueryWrapper<LegalHoldDocument>()
                        .eq(LegalHoldDocument::getTenantId, tenantId)
                        .eq(LegalHoldDocument::getDocumentId, documentId));
        if (links.isEmpty()) {
            return List.of();
        }
        // 第二段：关联保全中筛出 ACTIVE（保全中）的名称。
        List<Long> holdIds = links.stream().map(LegalHoldDocument::getLegalHoldId).distinct().toList();
        return legalHoldMapper.selectList(new LambdaQueryWrapper<LegalHold>()
                        .eq(LegalHold::getTenantId, tenantId)
                        .in(LegalHold::getId, holdIds)
                        .eq(LegalHold::getStatus, HOLD_ACTIVE))
                .stream()
                .map(LegalHold::getName)
                .toList();
    }

    /** schema 实体 → 视图（schema_json 解析；ACTIVE/RETIRED → 契约 PUBLISHED）。 */
    private MetadataSchemaVo toSchemaVo(MetadataSchema schema) {
        // 解析 schema_json：{"description": ..., "fields": [{key,label,type,required,options}, ...]}
        String description = null;
        List<MetadataFieldVo> fields = List.of();
        if (StringUtils.hasText(schema.getSchemaJson())) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(schema.getSchemaJson(), Map.class);
                description = (String) parsed.get("description");
                Object rawFields = parsed.get("fields");
                if (rawFields instanceof List<?> fieldList) {
                    fields = fieldList.stream()
                            .filter(field -> field instanceof Map)
                            .map(field -> {
                                Map<?, ?> fieldMap = (Map<?, ?>) field;
                                return new MetadataFieldVo(
                                        String.valueOf(fieldMap.get("key")),
                                        String.valueOf(fieldMap.get("label")),
                                        String.valueOf(fieldMap.get("type")),
                                        Boolean.TRUE.equals(fieldMap.get("required")),
                                        fieldMap.get("options") instanceof List<?> options
                                                ? options.stream().map(String::valueOf).toList()
                                                : List.of());
                            })
                            .toList();
                }
            } catch (Exception e) {
                // 历史脏 JSON 不阻断列表：字段降级为空并告警（数据问题显式暴露）。
                log.warn("metadata_schema JSON 解析失败 schemaId={}", schema.getId(), e);
            }
        }
        return new MetadataSchemaVo(
                schema.getId(),
                schema.getName(),
                description,
                fields,
                // 存储层 ACTIVE/RETIRED 均视为「已发布」展示（RETIRED 是被新版本取代的历史发布版本）。
                SCHEMA_DRAFT.equals(schema.getStatus()) ? SCHEMA_DRAFT : "PUBLISHED",
                schema.getUpdateTime() != null ? schema.getUpdateTime() : schema.getCreateTime());
    }

    /** 组装 schema_json（description 与字段定义一并序列化，保持插入顺序便于 diff）。 */
    private String writeSchemaJson(String description, List<MetadataFieldVo> fields) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("description", description);
        root.put("fields", fields);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // 序列化失败属程序缺陷（字段均为简单类型），快速失败防脏数据落库。
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "schema 定义序列化失败");
        }
    }

    /** 保留策略实体 → 视图（存储枚举 → 契约枚举；天 → 月）。 */
    private RetentionPolicyVo toPolicyVo(RetentionPolicy policy) {
        // scope_key 仅 KB 级解析为目标 id（CATEGORY 的 scope_key 为分类名，不映射数值 id；脏值容错为 null）。
        Long targetId = null;
        if ("KB".equals(policy.getScopeType()) && StringUtils.hasText(policy.getScopeKey())) {
            try {
                targetId = Long.parseLong(policy.getScopeKey().trim());
            } catch (NumberFormatException e) {
                // scope_key 非数值的历史脏数据：展示层降级为空目标，不阻断列表。
                targetId = null;
            }
        }
        int retentionDays = policy.getRetentionDays() != null ? policy.getRetentionDays() : 0;
        return new RetentionPolicyVo(
                policy.getId(),
                policy.getName(),
                SCOPE_TO_APPLIES.getOrDefault(policy.getScopeType(), policy.getScopeType()),
                targetId,
                // 存储单位天 → 契约单位月（四舍五入，30 天/月近似换算的逆运算；契约字段为 int）。
                (int) Math.round((double) retentionDays / DAYS_PER_MONTH),
                DISPOSITION_TO_ACTION.getOrDefault(policy.getDisposition(), policy.getDisposition()),
                POLICY_ACTIVE.equals(policy.getStatus()),
                policy.getCreateTime());
    }

    /** 保全实体 → 视图（补齐覆盖文档清单与创建人显示名）。 */
    private LegalHoldVo toHoldVo(LegalHold hold, List<Long> documentIds) {
        Map<Long, String> creatorNames = userNames(hold.getCreateBy() != null
                ? List.of(hold.getCreateBy()) : List.of());
        return new LegalHoldVo(
                hold.getId(),
                hold.getName(),
                documentIds,
                hold.getReason(),
                hold.getCreateBy() != null ? creatorNames.getOrDefault(hold.getCreateBy(), "") : "",
                hold.getCreateTime(),
                hold.getReleasedAt());
    }

    /** 保全覆盖的文档清单（关联表按保全 id 聚合）。 */
    private List<Long> documentIdsOf(LegalHold hold) {
        return legalHoldDocumentMapper.selectList(new LambdaQueryWrapper<LegalHoldDocument>()
                        .eq(LegalHoldDocument::getTenantId, hold.getTenantId())
                        .eq(LegalHoldDocument::getLegalHoldId, hold.getId()))
                .stream()
                .map(LegalHoldDocument::getDocumentId)
                .toList();
    }

    /** 删除任务实体 → 视图（状态/进度映射契约枚举，fileName 优先取任务快照）。 */
    private DeletionTaskVo toTaskVo(DeletionTask task, List<DeletionTarget> targets, String requesterName) {
        if (task == null) {
            return null;
        }
        long documentId = RESOURCE_DOCUMENT.equals(task.getResourceType())
                ? safeParseDocumentId(task.getResourceId()) : 0L;
        // fileName 优先读任务快照（软删后仍可展示）；QUEUED 未快照时回读文档现值，均缺失置空串。
        String fileName = parsePreviewFileName(task.getPreviewJson());
        if (!StringUtils.hasText(fileName) && documentId > 0 && !TASK_TERMINAL_STATUSES.contains(task.getStatus())) {
            try {
                fileName = documentServiceProvider.getObject().documentGovernanceBrief(documentId).fileName();
            } catch (Exception e) {
                // 文档已被删/不可读：快照缺失的历史任务降级空文件名，不阻断列表。
                fileName = "";
            }
        }
        return new DeletionTaskVo(
                task.getId(),
                documentId,
                fileName != null ? fileName : "",
                task.getReason(),
                requesterName,
                // 存储 DDL 状态 → 前端契约状态（BLOCKED 解除保全后可重审，归入待审批）。
                toContractTaskStatus(task.getStatus()),
                task.getCreateTime(),
                progressOf(targets));
    }

    /** 删除任务存储状态 → 契约状态（PENDING_APPROVAL/RUNNING/SUCCEEDED/FAILED）。 */
    private String toContractTaskStatus(String status) {
        if (status == null) {
            return "PENDING_APPROVAL";
        }
        return switch (status) {
            case TASK_RUNNING -> "RUNNING";
            case TASK_SUCCEEDED -> "SUCCEEDED";
            // BLOCKED 归入待审批（条件解除后可再次审批）；PARTIAL/CANCELLED 归入失败需人工复核。
            case TASK_QUEUED, TASK_BLOCKED -> "PENDING_APPROVAL";
            default -> "FAILED";
        };
    }

    /** 进度视图：各存储层目标是否已处置完成（本实现覆盖 storage/index，cache/backup 为预留层）。 */
    private DeletionProgressVo progressOf(List<DeletionTarget> targets) {
        boolean storage = false;
        boolean index = false;
        for (DeletionTarget target : targets) {
            // 完成态 = SUCCEEDED / SKIPPED（跳过也算该层收口）；RETAINED 是保全保留，不算完成。
            boolean done = TARGET_SUCCEEDED.equals(target.getStatus()) || TARGET_SKIPPED.equals(target.getStatus());
            if (TARGET_OBJECT.equals(target.getTargetType())) {
                // 存储层：document 行软删 + 对象引用摘除（对象原文按保留期另行物理清理）。
                storage = done;
            } else if (TARGET_SEARCH_INDEX.equals(target.getTargetType())) {
                // 索引层：rag-engine 向量删除。
                index = done;
            }
        }
        // cache/backup 两层暂无实现（缓存与备份副本清理为预留能力），如实置 false。
        return new DeletionProgressVo(storage, index, false, false);
    }

    /** 删除证明实体 → 视图（documentId/fileName 取任务快照，operator 取 summary 留档人）。 */
    private DeletionReceiptVo toReceiptVo(DeletionReceipt receipt, DeletionTask task,
                                          Map<Long, String> operatorNames) {
        long documentId = 0L;
        String fileName = "";
        Long operatorId = parseOperatorId(receipt.getSummaryJson());
        if (task != null) {
            // 任务快照是软删后的展示数据源（documentId 解析 + fileName 快照）。
            documentId = RESOURCE_DOCUMENT.equals(task.getResourceType())
                    ? safeParseDocumentId(task.getResourceId()) : 0L;
            fileName = parsePreviewFileName(task.getPreviewJson());
        }
        return new DeletionReceiptVo(
                // 契约 id 为字符串：表主键转字符串返回（Controller 按字符串等值匹配）。
                String.valueOf(receipt.getId()),
                receipt.getDeletionTaskId(),
                documentId,
                fileName != null ? fileName : "",
                receipt.getReceiptSha256(),
                receipt.getCreateTime(),
                operatorId != null ? operatorNames.getOrDefault(operatorId, "") : "");
    }

    /** 确保 OBJECT 目标行存在（无则按指定初始状态登记；有则按需更新状态）。 */
    private DeletionTarget ensureObjectTarget(DeletionTask task, long documentId, String status) {
        return ensureTarget(task, TARGET_OBJECT, documentId, status);
    }

    /** 确保 SEARCH_INDEX 目标行存在（无则登记 PENDING；向量清理由异步收尾推进）。 */
    private DeletionTarget ensureSearchIndexTarget(DeletionTask task, long documentId) {
        return ensureTarget(task, TARGET_SEARCH_INDEX, documentId, TARGET_PENDING);
    }

    /** 通用目标行登记：按 (任务, 类型, 目标键) 查既有行，命中则更新状态，未命中插入。 */
    private DeletionTarget ensureTarget(DeletionTask task, String targetType, long documentId, String status) {
        DeletionTarget existing = deletionTargetMapper.selectOne(new LambdaQueryWrapper<DeletionTarget>()
                .eq(DeletionTarget::getTenantId, task.getTenantId())
                .eq(DeletionTarget::getDeletionTaskId, task.getId())
                .eq(DeletionTarget::getTargetType, targetType)
                .eq(DeletionTarget::getTargetKey, String.valueOf(documentId))
                .last("LIMIT 1"));
        if (existing != null) {
            // 幂等更新状态（BLOCKED 重试场景复用同一目标行）。
            if (!status.equals(existing.getStatus())) {
                existing.setStatus(status);
                deletionTargetMapper.updateById(existing);
            }
            return existing;
        }
        DeletionTarget target = new DeletionTarget();
        target.setTenantId(task.getTenantId());
        target.setDeletionTaskId(task.getId());
        target.setTargetType(targetType);
        // 目标键：文档级任务统一用 documentId 字符串（KB 级扩展时换 kb 命名空间键）。
        target.setTargetKey(String.valueOf(documentId));
        target.setStatus(status);
        target.setExpectedCount(1L);
        target.setDeletedCount(0L);
        try {
            deletionTargetMapper.insert(target);
        } catch (DuplicateKeyException e) {
            // 并发登记撞 uq_deletion_target：重查取既有行（幂等收口）。
            return deletionTargetMapper.selectOne(new LambdaQueryWrapper<DeletionTarget>()
                    .eq(DeletionTarget::getDeletionTaskId, task.getId())
                    .eq(DeletionTarget::getTargetType, targetType)
                    .eq(DeletionTarget::getTargetKey, String.valueOf(documentId))
                    .last("LIMIT 1"));
        }
        return target;
    }

    /** 目标行处置完成回写（状态 + 计数 + 完成时间 + 证据摘要/错误码）。 */
    private void completeTarget(DeletionTarget target, String status, String resultSha256) {
        target.setStatus(status);
        if (TARGET_SUCCEEDED.equals(status) || TARGET_SKIPPED.equals(status)) {
            target.setDeletedCount(1L);
        }
        target.setResultSha256(resultSha256);
        target.setCompletedAt(Instant.now());
        deletionTargetMapper.updateById(target);
    }

    /** 删除任务快照（preview_json）：软删前留档展示与证明所需的文档元数据。 */
    private String writePreviewJson(DocumentService.DocumentGovernanceBrief brief, String phase) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("phase", phase);
        preview.put("documentId", brief.documentId());
        preview.put("kbId", brief.kbId());
        preview.put("title", brief.title());
        preview.put("fileName", brief.fileName());
        preview.put("sensitivity", brief.sensitivity());
        preview.put("snapshottedAt", Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(preview);
        } catch (Exception e) {
            // 序列化失败属程序缺陷（简单类型），快速失败防脏数据落库。
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "删除任务快照序列化失败");
        }
    }

    /** 解析任务快照中的 fileName（无快照或脏 JSON 返回 null，由调用方降级）。 */
    private String parsePreviewFileName(String previewJson) {
        return parseStringField(previewJson, "fileName");
    }

    /** 解析证明 summary 中的操作人 id（无或脏数据返回 null）。 */
    private Long parseOperatorId(String summaryJson) {
        String value = parseStringField(summaryJson, "operatorId");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 JSON 字符串解析顶层字段值（容错：任何解析异常返回 null）。 */
    private String parseStringField(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            Object value = parsed.get(field);
            return value != null ? String.valueOf(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 resource_id（VARCHAR）为 documentId（格式非法按参数错误拒绝，用于审批执行路径）。 */
    private long parseDocumentId(String resourceId) {
        try {
            return Long.parseLong(resourceId);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "删除任务的资源标识非法: " + resourceId);
        }
    }

    /** 列表/视图组装用的容错解析（脏数据降级为 0，不阻断整页列表）。 */
    private long safeParseDocumentId(String resourceId) {
        try {
            return Long.parseLong(resourceId);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 审批意见提取（approve 允许为空，reject 由文档侧强制非空）。 */
    private String commentOf(ReviewActionDto request) {
        return request != null && StringUtils.hasText(request.comment())
                ? request.comment().trim() : null;
    }

    /**
     * 删除收尾注册：事务活跃时挂到提交后回调（避免主事务回滚后向量已被物理清除），
     * 无事务上下文则立即投递（与 KbServiceImpl 的向量清理模式一致）。
     */
    private void registerDeletionFinalizationAfterCommit(long tenantId, long taskId, long documentId,
                                                         Long operatorId, Instant approvedAt) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 提交成功后才触发收尾（向量删除不可逆，不能先于事务结果执行）。
                    submitDeletionFinalization(tenantId, taskId, documentId, operatorId, approvedAt);
                }
            });
        } else {
            submitDeletionFinalization(tenantId, taskId, documentId, operatorId, approvedAt);
        }
    }

    /** 收尾任务投递到单线程执行器：清向量 → 推进目标行 → 汇总任务终态 → 生成删除证明。 */
    private void submitDeletionFinalization(long tenantId, long taskId, long documentId,
                                            Long operatorId, Instant approvedAt) {
        deletionFinalizerExecutor.execute(() -> finalizeDeletion(tenantId, taskId, documentId, operatorId, approvedAt));
    }

    /** 删除收尾执行（后台线程，无外层事务；各步骤独立落库，失败仅告警不回滚已完成的步骤）。 */
    private void finalizeDeletion(long tenantId, long taskId, long documentId, Long operatorId, Instant approvedAt) {
        // ① 索引层处置：调用 rag-engine 删除该文档全部版本向量（versionNo=null 语义）。
        DeletionTask task = deletionTaskMapper.selectById(taskId);
        DeletionTarget indexTarget = deletionTargetMapper.selectOne(new LambdaQueryWrapper<DeletionTarget>()
                .eq(DeletionTarget::getDeletionTaskId, taskId)
                .eq(DeletionTarget::getTargetType, TARGET_SEARCH_INDEX)
                .last("LIMIT 1"));
        try {
            ragEnginePort.deleteVectors(new TenantId(tenantId), documentId, null);
            if (indexTarget != null) {
                // 索引目标完成：证据摘要 = 文档 id + 任务 id + 完成时间戳的规范化串。
                completeTarget(indexTarget, TARGET_SUCCEEDED,
                        sha256Hex("vectors:" + documentId + ":task:" + taskId + ":deleted:" + Instant.now()));
            }
        } catch (Exception e) {
            // 向量清理失败：目标行落 FAILED + 错误码，任务将收敛为 PARTIAL（残留向量待补偿任务回收）。
            log.warn("删除审批向量清理失败 tenantId={} taskId={} documentId={}", tenantId, taskId, documentId, e);
            if (indexTarget != null) {
                indexTarget.setStatus(TARGET_FAILED);
                indexTarget.setLastErrorCode("RAG_ENGINE_ERROR");
                indexTarget.setCompletedAt(Instant.now());
                deletionTargetMapper.updateById(indexTarget);
            }
        }
        // ② 汇总任务终态：按目标行完成/失败计数推进（completed + failed <= target_count）。
        List<DeletionTarget> targets = deletionTargetMapper.selectList(
                new LambdaQueryWrapper<DeletionTarget>().eq(DeletionTarget::getDeletionTaskId, taskId));
        long completed = targets.stream()
                .filter(target -> TARGET_SUCCEEDED.equals(target.getStatus()) || TARGET_SKIPPED.equals(target.getStatus()))
                .count();
        long failed = targets.stream().filter(target -> TARGET_FAILED.equals(target.getStatus())).count();
        // 终态判定：无失败→SUCCEEDED；有失败且有完成→PARTIAL；全失败→FAILED。
        String finalStatus = failed == 0 ? TASK_SUCCEEDED : (completed > 0 ? TASK_PARTIAL : TASK_FAILED);
        Instant completedAt = Instant.now();
        // 直更列（绕开乐观锁读改写竞态）：后台单线程推进，列级 set 保持幂等。
        deletionTaskMapper.update(null, new LambdaUpdateWrapper<DeletionTask>()
                .eq(DeletionTask::getId, taskId)
                .set(DeletionTask::getStatus, finalStatus)
                .set(DeletionTask::getCompletedCount, (int) completed)
                .set(DeletionTask::getFailedCount, (int) failed)
                .set(DeletionTask::getCompletedAt, completedAt));
        // ③ 生成删除证明（deletion_receipt 追加写，一任务一条；BLOCKED 中途不落证明）。
        writeDeletionReceipt(tenantId, task, targets, operatorId, approvedAt, completedAt, finalStatus, documentId);
    }

    /** 组装并落库删除证明（summary_json + receipt_sha256，对齐 ck_deletion_receipt 约束）。 */
    private void writeDeletionReceipt(long tenantId, DeletionTask task, List<DeletionTarget> targets,
                                      Long operatorId, Instant approvedAt, Instant completedAt,
                                      String finalStatus, long documentId) {
        // 幂等防御：证明已存在（收尾重放）则跳过（uq_deletion_receipt_task 一任务一条）。
        Long existing = deletionReceiptMapper.selectCount(new LambdaQueryWrapper<DeletionReceipt>()
                .eq(DeletionReceipt::getDeletionTaskId, task != null ? task.getId() : 0L));
        if (existing != null && existing > 0) {
            return;
        }
        // 证明摘要：操作人/时间/对象清单逐项留档（对象清单含各存储层处置状态与证据摘要）。
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", task != null ? task.getId() : null);
        summary.put("documentId", documentId);
        summary.put("fileName", task != null ? parsePreviewFileName(task.getPreviewJson()) : null);
        summary.put("operatorId", operatorId);
        summary.put("approvedAt", approvedAt != null ? approvedAt.toString() : null);
        summary.put("completedAt", completedAt.toString());
        summary.put("targets", targets.stream()
                .map(target -> Map.of(
                        "type", target.getTargetType(),
                        "status", target.getStatus(),
                        "sha256", target.getResultSha256() != null ? target.getResultSha256() : ""))
                .toList());
        String summaryJson;
        try {
            summaryJson = objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            // 序列化失败属程序缺陷：告警并放弃本条证明（任务终态已落库，证明可人工补录）。
            log.error("删除证明 summary 序列化失败 taskId={}", task != null ? task.getId() : null, e);
            return;
        }
        DeletionReceipt receipt = new DeletionReceipt();
        receipt.setTenantId(tenantId);
        receipt.setDeletionTaskId(task != null ? task.getId() : 0L);
        // 证明结果枚举（ck_deletion_receipt_result）：全成功 SUCCEEDED / 部分成功 PARTIAL。
        receipt.setResult(TASK_PARTIAL.equals(finalStatus) ? TASK_PARTIAL : TASK_SUCCEEDED);
        receipt.setSummaryJson(summaryJson);
        // 证明指纹：summary_json UTF-8 字节的 SHA-256（供审计方独立核验内容未被篡改）。
        receipt.setReceiptSha256(sha256Hex(summaryJson));
        try {
            deletionReceiptMapper.insert(receipt);
        } catch (DuplicateKeyException e) {
            // 并发收尾撞一任务一条约束：按已存在处理（幂等收口）。
            log.info("删除证明已存在，跳过重放 taskId={}", task != null ? task.getId() : null);
        }
    }

    /** SHA-256 十六进制小写摘要（CHAR(64) 列约束：64 位小写十六进制）。 */
    private String sha256Hex(String content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 批量解析租户内用户显示名；identity 服务未装配（db 关闭）时返回空映射（展示名降级空串）。 */
    private Map<Long, String> userNames(List<Long> userIds) {
        UserAccountService userAccountService = userAccountServiceProvider.getIfAvailable();
        if (userAccountService == null || userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountService.displayNamesOf(effectiveTenantId(), userIds);
    }

    /** 单个操作人显示名（approveDeletion 即时返回场景）。 */
    private String operatorNames(Long operatorId) {
        if (operatorId == null) {
            return "";
        }
        return userNames(List.of(operatorId)).getOrDefault(operatorId, "");
    }

    /** 写入用有效租户：当前认证租户优先，dev/API Key/未认证兜底默认租户 1（种子数据）。 */
    private long effectiveTenantId() {
        Long tenantId = currentTenantIdOrNull();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    /** 当前 JWT 主体的租户 id；dev/API Key/未认证返回 null（此时查询不强制租户过滤）。 */
    private Long currentTenantIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal
                && principal.tenantId() > 0) {
            return principal.tenantId();
        }
        return null;
    }
}
