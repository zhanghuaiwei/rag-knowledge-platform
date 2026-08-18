package com.ragkb.service.modules.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.UserId;
import com.ragkb.service.common.security.SecurityUtils;
import com.ragkb.service.modules.document.dto.AclSetDto;
import com.ragkb.service.modules.document.dto.DeletionDto;
import com.ragkb.service.modules.document.dto.RollbackVersionDto;
import com.ragkb.service.modules.document.dto.UpdateDocumentMetadataDto;
import com.ragkb.service.modules.document.dto.UploadInitDto;
import com.ragkb.service.modules.document.persistence.entity.Document;
import com.ragkb.service.modules.document.persistence.entity.DocumentAcl;
import com.ragkb.service.modules.document.persistence.entity.DocumentReview;
import com.ragkb.service.modules.document.persistence.entity.DocumentTag;
import com.ragkb.service.modules.document.persistence.entity.DocumentVersion;
import com.ragkb.service.modules.document.persistence.entity.Tag;
import com.ragkb.service.modules.document.persistence.entity.UserFavorite;
import com.ragkb.service.modules.document.persistence.mapper.DocumentAclMapper;
import com.ragkb.service.modules.document.persistence.mapper.DocumentMapper;
import com.ragkb.service.modules.document.persistence.mapper.DocumentReviewMapper;
import com.ragkb.service.modules.document.persistence.mapper.DocumentTagMapper;
import com.ragkb.service.modules.document.persistence.mapper.DocumentVersionMapper;
import com.ragkb.service.modules.document.persistence.mapper.TagMapper;
import com.ragkb.service.modules.document.persistence.mapper.UserFavoriteMapper;
import com.ragkb.service.modules.document.service.DocumentService;
import com.ragkb.service.modules.document.vo.AclEntryVo;
import com.ragkb.service.modules.document.vo.DocumentDetailVo;
import com.ragkb.service.modules.document.vo.DocumentSummaryVo;
import com.ragkb.service.modules.document.vo.DocumentVersionVo;
import com.ragkb.service.modules.document.vo.FavoriteItemVo;
import com.ragkb.service.modules.document.vo.TagVo;
import com.ragkb.service.modules.document.vo.UploadInitResponseVo;
import com.ragkb.service.modules.ingestion.service.IngestionUseCase;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.access.service.AccessPolicyUseCase;
import com.ragkb.service.modules.infrastructure.port.ObjectStorePort;
import com.ragkb.service.modules.knowledge.service.KbService;
import com.ragkb.service.modules.task.service.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档域用例实现：上传（init/parts/complete）与文档生命周期管理。
 *
 * <p>上传链路（对齐 {@code docs/api/server.openapi.yaml} 与
 * {@code docs/modules/enterprise-generalization/design/document-upload-data-flow.md}）：
 * <pre>
 *   ① POST /api/v1/upload/init   校验目标知识库/文件名/大小/敏感级，创建上传会话
 *   ② PUT  /upload/{id}/parts/{n} 分片落盘（幂等：同号覆盖），partSize=0 表示直传
 *   ③ POST /upload/{id}/complete  合并分片 → SHA-256 → 写对象存储 →
 *                                  事务写 document/document_version/parse_task/outbox →
 *                                  返回 SUCCEEDED 任务（resourceId=documentId 供前端回读）
 * </pre>
 * 原始字节只进对象存储（本地开发为 {@link com.ragkb.service.modules.infrastructure.adapter.LocalObjectStore}），
 * 数据库只存 {@code object_key + content_sha256}（不可逆摘要，DDL 注释边界）。
 *
 * <p>⚠️ 说明：
 * <ul>
 *   <li>摄取（扫描/解析/分块/embedding）由 rag-engine worker 消费 outbox 推进，
 *       {@code completeUpload} 只负责排队，不假报已解析；</li>
 *   <li>租户默认取目标知识库（上传）或当前 JWT 主体（列表/详情）；未认证的 dev/API Key
 *       场景不强制租户过滤（与 {@code KbServiceImpl} 现状一致）；</li>
 *   <li>{@code ownerName / createdBy} 展示字段待接入身份目录按 userId 查名，当前置空。</li>
 * </ul>
 */
// 装配条件：本实现依赖 MyBatis Mapper（仅 ragkb.db.enabled=true 时注册），为避免
// scaffold 模式（无数据库）上下文装配失败，与所属 Controller 一起按同一开关条件装配。
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class DocumentServiceImpl implements DocumentService {

    // ---------- 上传/文档校验常量（与服务端安全基线一致） ----------

    /** 单文件大小上限：50MB（与前端 upload-document-modal.tsx 一致）。 */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    /** 超过该阈值走分片上传，否则直传（partSize=0）。 */
    private static final long MULTIPART_PART_SIZE = 8L * 1024 * 1024;
    /** 允许的文件扩展名（与前端 accept 一致）。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "md", "txt", "pptx", "xlsx", "csv", "html");
    /** 敏感级枚举（document.sensitivity CHECK）。 */
    private static final Set<String> SENSITIVITY_LEVELS = Set.of(
            "PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");
    /** 文档生命周期状态（document.lifecycle_status CHECK）。 */
    private static final String LIFECYCLE_ACTIVE = "ACTIVE";
    private static final String LIFECYCLE_DELETING = "DELETING";
    /** 摄取状态枚举（document_version.ingest_status CHECK；列表过滤白名单，防注入）。 */
    private static final Set<String> INGEST_STATUSES = Set.of(
            "QUARANTINED", "SCANNING", "PARSING", "CHUNKING", "EMBEDDING",
            "INDEXING", "READY", "FAILED", "BLOCKED");

    // ---------- 依赖 ----------

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final DocumentAclMapper documentAclMapper;
    private final DocumentTagMapper documentTagMapper;
    private final TagMapper tagMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final DocumentReviewMapper documentReviewMapper;
    private final UploadSessionStore uploadSessionStore;
    private final ObjectStorePort objectStore;
    private final KbService kbService;
    private final IngestionUseCase ingestionUseCase;
    private final TaskService taskService;
    private final AccessPolicyUseCase accessPolicy;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               DocumentVersionMapper documentVersionMapper,
                               DocumentAclMapper documentAclMapper,
                               DocumentTagMapper documentTagMapper,
                               TagMapper tagMapper,
                               UserFavoriteMapper userFavoriteMapper,
                               DocumentReviewMapper documentReviewMapper,
                               UploadSessionStore uploadSessionStore,
                               ObjectStorePort objectStore,
                               KbService kbService,
                               IngestionUseCase ingestionUseCase,
                               TaskService taskService,
                               AccessPolicyUseCase accessPolicy) {
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.documentAclMapper = documentAclMapper;
        this.documentTagMapper = documentTagMapper;
        this.tagMapper = tagMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.documentReviewMapper = documentReviewMapper;
        this.uploadSessionStore = uploadSessionStore;
        this.objectStore = objectStore;
        this.kbService = kbService;
        this.ingestionUseCase = ingestionUseCase;
        this.taskService = taskService;
        this.accessPolicy = accessPolicy;
    }

    // =====================================================================
    // 查询：列表 / 详情 / 版本
    // =====================================================================

    @Override
    public PageData<DocumentSummaryVo> listDocuments(Long kbId, String reviewStatus, String ingestStatus,
                                                     String sensitivity, String keyword, Long tagId, int page, int size) {
        Long tenantId = currentTenantIdOrNull();
        // 摄取状态是白名单枚举：先校验再拼子查询，杜绝注入。
        if (StringUtils.hasText(ingestStatus) && !INGEST_STATUSES.contains(ingestStatus)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的摄取状态: " + ingestStatus);
        }
        // 单表分页 + 过滤；标签/摄取状态过滤用 inSql 子查询
        // （ingest_status 属 document_version，按 current_version_id 关联当前版本，避免先查后滤的分页失真）。
        IPage<Document> documentPage = documentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Document>()
                        .eq(tenantId != null, Document::getTenantId, tenantId)
                        .eq(kbId != null, Document::getKbId, kbId)
                        .eq(StringUtils.hasText(reviewStatus), Document::getReviewStatus, reviewStatus)
                        .inSql(StringUtils.hasText(ingestStatus), Document::getId,
                                "SELECT document_id FROM document_version "
                                        + "WHERE id = document.current_version_id AND ingest_status = '" + ingestStatus + "'")
                        .eq(StringUtils.hasText(sensitivity), Document::getSensitivity, sensitivity)
                        .and(StringUtils.hasText(keyword),
                                wrapper -> wrapper.like(Document::getTitle, keyword)
                                        .or().like(Document::getFileName, keyword))
                        .inSql(tagId != null, Document::getId,
                                "SELECT document_id FROM document_tag WHERE tag_id = " + tagId)
                        .orderByDesc(Document::getCreateTime));
        List<Document> documents = documentPage.getRecords();
        if (documents.isEmpty()) {
            return PageData.empty(page, size);
        }
        return PageData.of(toSummaries(documents), documentPage.getTotal(), page, size);
    }

    @Override
    public DocumentDetailVo getDocument(long documentId) {
        Document document = requireDocument(documentId);
        DocumentVersion version = requireCurrentVersion(document);
        // 版本历史 + 标签 + 收藏状态一次聚齐，避免前端多次往返。
        List<DocumentVersion> versions = documentVersionMapper.selectList(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getDocumentId, documentId)
                        .orderByDesc(DocumentVersion::getVersionNo));
        List<String> tags = tagNamesOf(documentId);
        boolean favorite = isFavorite(documentId);
        return toDetail(document, version, tags, versions, favorite);
    }

    @Override
    public List<DocumentVersionVo> listDocumentVersions(long documentId) {
        requireDocument(documentId);
        return documentVersionMapper.selectList(new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getDocumentId, documentId)
                        .orderByDesc(DocumentVersion::getVersionNo))
                .stream()
                .map(this::toVersionVo)
                .toList();
    }

    // =====================================================================
    // 上传：init → parts → complete
    // =====================================================================

    @Override
    public UploadInitResponseVo initUpload(UploadInitDto request, String idempotencyKey) {
        // ① 目标知识库必须存在且可用（租户/数据地域以知识库为准，不信任客户端自报）。
        KbService.KbBrief kb = kbService.kbBrief(request.kbId());
        Long currentTenantId = currentTenantIdOrNull();
        // 知识库已归属租户（tenantId>0）且当前请求携带租户时才做匹配；
        // kb.tenantId()==0 表示未归属租户（dev/种子数据），跳过校验不阻断流程。
        if (currentTenantId != null && kb.tenantId() > 0 && !currentTenantId.equals(kb.tenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权向该知识库上传");
        }
        if (!"ACTIVE".equals(kb.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "知识库当前不可用，无法上传");
        }
        // ② 文件元数据校验（扩展名 / 大小 / 敏感级）。
        String fileName = request.fileName().trim();
        String fileExt = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(fileExt.toLowerCase())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "不支持的文件类型 ." + fileExt + "，允许: " + String.join(" / ", ALLOWED_EXTENSIONS));
        }
        if (request.fileSize() <= 0 || request.fileSize() > MAX_FILE_SIZE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "文件大小必须为 1B ~ 50MB");
        }
        String sensitivity = StringUtils.hasText(request.sensitivity()) ? request.sensitivity() : "INTERNAL";
        if (!SENSITIVITY_LEVELS.contains(sensitivity)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的敏感级: " + sensitivity);
        }
        // ③ 幂等：同一 Idempotency-Key 的未完成会话直接返回（同文件重复上传=秒传/续传）。
        if (idempotencyKey != null) {
            UploadSessionStore.Session existing =
                    uploadSessionStore.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                return new UploadInitResponseVo(existing.uploadId, existing.partSize, existing.partCount,
                        existingUploadedParts(existing), null);
            }
        }
        // ④ 分片策略：<=8MB 直传（partSize=0 单分片），否则按 8MB 分片（可断点续传）。
        long partSize = request.fileSize() <= MULTIPART_PART_SIZE ? 0 : MULTIPART_PART_SIZE;
        int partCount = partSize == 0 ? 1 : (int) Math.ceil((double) request.fileSize() / partSize);

        String title = StringUtils.hasText(request.title()) ? request.title().trim() : baseName(fileName);
        String uploadId = UploadSessionStore.newUploadId();
        UploadSessionStore.Session session = new UploadSessionStore.Session(
                uploadId, kb.tenantId(), SecurityUtils.currentUserId(), kb.id(),
                fileName, request.fileSize(),
                StringUtils.hasText(request.mimeType()) ? request.mimeType() : mimeTypeOf(fileExt),
                title, sensitivity, request.sha256(), partSize, partCount, idempotencyKey, Instant.now());
        uploadSessionStore.create(session);
        return new UploadInitResponseVo(uploadId, partSize, partCount, List.of(), null);
    }

    @Override
    public void uploadPart(String uploadId, int partNumber, byte[] content) {
        UploadSessionStore.Session session = uploadSessionStore.get(uploadId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "上传会话不存在或已过期，请重新上传"));
        if (session.completedTaskId != null) {
            throw new ApiException(ErrorCode.CONFLICT, "该上传已完成，请勿重复上传分片");
        }
        if (content == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "分片内容为空");
        }
        // 幂等语义：同分片号重复上传直接覆盖（断点续传场景前端会重发）。
        uploadSessionStore.putPart(session, partNumber, content);
    }

    @Override
    @Transactional
    public Task completeUpload(String uploadId, String idempotencyKey) {
        UploadSessionStore.Session session = uploadSessionStore.get(uploadId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "上传会话不存在或已过期，请重新上传"));
        // 幂等：同一会话重复 complete 直接返回原任务（前端轮询重试场景）。
        if (session.completedTaskId != null) {
            return taskService.getTask(session.completedTaskId);
        }
        if (!uploadSessionStore.isComplete(session)) {
            Set<Integer> got = uploadSessionStore.uploadedParts(uploadId);
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "分片不完整：已上传 " + got.size() + "/" + session.partCount + "，请先上传全部或重试");
        }
        // ① 合并分片并重算 SHA-256（客户端预计算的 sha256 仅用于秒传提示，完成以服务端为准）。
        byte[] merged = uploadSessionStore.merge(session)
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "分片合并失败，请重新上传"));
        if (merged.length != session.fileSize) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "分片字节数不符：期望 " + session.fileSize + "，实际 " + merged.length);
        }
        String sha256 = sha256Hex(merged);
        String objectKey = buildObjectKey(session);

        // ② 事务落库：document + document_version + parse_task + outbox（同事务，ADR-2 可靠性）。
        // 租户一致性：document.tenant_id 必须与 kb.tenant_id 完全一致（外键 fk_document_kb 检查 (tenant_id, kb_id)）。
        // 若 kb 的 tenant_id 为 0/null（旧数据/未归属租户），先就地修正为默认租户 1，避免外键冲突。
        KbService.KbBrief kbNow = kbService.kbBrief(session.kbId);
        long effectiveTenantId = kbNow.tenantId() > 0 ? kbNow.tenantId() : DEFAULT_TENANT_ID;
        Document document = new Document();
        document.setTenantId(effectiveTenantId);
        document.setKbId(session.kbId);
        document.setTitle(session.title);
        document.setFileName(session.fileName);
        document.setFileExt(extensionOf(session.fileName));
        document.setMimeType(session.mimeType);
        document.setSourceType("UPLOAD");
        document.setOwnerUserId(session.userId);
        document.setLifecycleStatus(LIFECYCLE_ACTIVE);
        document.setReviewStatus("DRAFT");
        document.setAuthorityStatus("DRAFT");
        document.setSensitivity(session.sensitivity);
        document.setDataRegion(kbNow.dataRegion());
        documentMapper.insert(document);

        DocumentVersion version = new DocumentVersion();
        version.setTenantId(effectiveTenantId);
        version.setDocumentId(document.getId());
        version.setVersionNo(1);
        version.setObjectKey(objectKey);
        version.setContentSha256(sha256);
        version.setFileSize(session.fileSize);
        version.setMimeType(session.mimeType);
        version.setIngestStatus("QUARANTINED"); // 进入隔离区，等待安全扫描（GKB-03）
        version.setSafetyStatus("PENDING");
        version.setChunkCount(0);
        documentVersionMapper.insert(version);

        // 回填当前版本指针（document_version 外键需要先有版本行）。
        document.setCurrentVersionId(version.getId());
        documentMapper.updateById(document);

        // 入队安全摄取：parse_task（SAFETY/QUEUED）+ outbox（topic=ingestion）。
        TenantId tenantId = requireTenantId(session.tenantId);
        ingestionUseCase.enqueueIngestion(tenantId, document.getId(), version.getId(),
                IngestionUseCase.TASK_TYPE_INGEST, session.idempotencyKey);

        // ③ 写对象存储（不可变原文；事务失败时回滚 DB，文件成为孤儿由清理任务回收）。
        objectStore.put(tenantId, objectKey,
                new ByteArrayInputStream(merged), merged.length, session.mimeType);

        // ④ 返回 SUCCEEDED 任务：resourceId=documentId，前端 waitForTask 后回读文档详情。
        Task task = taskService.submit("UPLOAD", "SUCCEEDED",
                "「" + session.title + "」上传完成，已进入解析队列", 100,
                "DOCUMENT", String.valueOf(document.getId()), null);
        session.completedTaskId = task.id();
        uploadSessionStore.remove(uploadId); // 清理分片临时文件与会话
        return task;
    }

    // =====================================================================
    // 文档元数据 / 生命周期
    // =====================================================================

    @Override
    @Transactional
    public DocumentDetailVo updateDocumentMetadata(long documentId, UpdateDocumentMetadataDto request) {
        Document document = requireDocument(documentId);
        if (StringUtils.hasText(request.title())) {
            document.setTitle(request.title().trim());
        }
        if (StringUtils.hasText(request.sensitivity())) {
            if (!SENSITIVITY_LEVELS.contains(request.sensitivity())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "非法的敏感级: " + request.sensitivity());
            }
            document.setSensitivity(request.sensitivity());
        }
        documentMapper.updateById(document);
        // 标签为覆盖式写入：先清空再按名称重建（名称已存在则复用，不重复建）。
        if (request.tags() != null) {
            replaceTags(documentId, request.tags());
        }
        return getDocument(documentId);
    }

    @Override
    @Transactional
    public Task deleteDocument(long documentId, DeletionDto request, String idempotencyKey) {
        Document document = requireDocument(documentId);
        // 逻辑删除：lifecycle=DELETING + del_flag=1（DDL ck_document_del_flag 要求两者一致）。
        document.setLifecycleStatus(LIFECYCLE_DELETING);
        document.setDelFlag(1);
        documentMapper.updateById(document);
        // 物理清理（对象存储/索引/缓存）由 deletion_task + rag-engine CLEANUP 异步执行，
        // 此处仅登记完成态任务供前端确认；request.reason() 留痕于审计（当前仅日志）。
        return taskService.submit("DELETE", "SUCCEEDED", "文档删除申请已受理", 100,
                "DOCUMENT", String.valueOf(documentId), request.reason());
    }

    @Override
    @Transactional
    public List<Long> softDeleteDocumentsByKb(long tenantId, long kbId) {
        // ① 先查出该库下全部未删文档 id（@TableLogic 自动过滤 del_flag=1），供调用方清理向量使用。
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .eq(Document::getKbId, kbId)
                .select(Document::getId));
        if (documents.isEmpty()) {
            // 库内无文档：无需标记，直接返回空清单（幂等）。
            return List.of();
        }
        // ② 批量软删标记：lifecycle=DELETING + del_flag=1（ck_document_del_flag 要求两列一致；
        //    del_flag 为 @TableLogic 字段、实体更新不落 SET，故用 wrapper setSql 显式置 1）。
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .eq(Document::getKbId, kbId)
                .set(Document::getLifecycleStatus, LIFECYCLE_DELETING)
                .setSql("del_flag = 1"));
        // ③ 返回受影响文档 id（对象存储原文与向量的物理清理由调用方异步执行）。
        return documents.stream().map(Document::getId).toList();
    }

    @Override
    @Transactional
    public Task reparseDocument(long documentId, String idempotencyKey) {
        Document document = requireDocument(documentId);
        DocumentVersion version = requireCurrentVersion(document);
        // 重置为隔离态并重新入队（解析失败重试上限 3 次由 rag-engine worker 控制）。
        version.setIngestStatus("QUARANTINED");
        version.setSafetyStatus("PENDING");
        version.setErrorCode(null);
        version.setErrorDetail(null);
        documentVersionMapper.updateById(version);
        String key = idempotencyKey != null ? idempotencyKey : "reparse-" + documentId + "-" + version.getId();
        ingestionUseCase.enqueueIngestion(requireTenantId(document.getTenantId()), documentId, version.getId(),
                IngestionUseCase.TASK_TYPE_REPARSE, key);
        return taskService.submit("REPARSE", "SUCCEEDED", "文档已重新进入解析队列", 100,
                "DOCUMENT", String.valueOf(documentId), null);
    }

    @Override
    @Transactional
    public DocumentDetailVo rollbackVersion(long documentId, RollbackVersionDto request) {
        Document document = requireDocument(documentId);
        // 目标版本必须是该文档的既有版本；回滚语义 = current_version_id 切回旧版本对象。
        DocumentVersion target = documentVersionMapper.selectOne(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId)
                .eq(DocumentVersion::getVersionNo, request.versionNo())
                .last("LIMIT 1"));
        if (target == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "目标版本不存在");
        }
        document.setCurrentVersionId(target.getId());
        documentMapper.updateById(document);
        return getDocument(documentId);
    }

    @Override
    @Transactional
    public void submitForReview(long documentId) {
        Document document = requireDocument(documentId);
        if ("PUBLISHED".equals(document.getReviewStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "文档已发布，无需重复送审");
        }
        document.setReviewStatus("PENDING_REVIEW");
        documentMapper.updateById(document);
        // 审核留痕（追加写证据表，DDL 约定 REVOKE UPDATE/DELETE 保持不可变）。
        DocumentReview review = new DocumentReview();
        review.setTenantId(document.getTenantId());
        review.setDocumentId(documentId);
        review.setVersionId(document.getCurrentVersionId());
        review.setAction("SUBMIT");
        review.setActorUserId(SecurityUtils.currentUserId());
        review.setPolicyVersion(document.getPolicyVersion());
        documentReviewMapper.insert(review);
    }

    @Override
    @Transactional
    public void disableDocument(long documentId) {
        Document document = requireDocument(documentId);
        document.setIsDisabled(true);
        documentMapper.updateById(document);
    }

    @Override
    @Transactional
    public void enableDocument(long documentId) {
        Document document = requireDocument(documentId);
        document.setIsDisabled(false);
        documentMapper.updateById(document);
    }

    // =====================================================================
    // 治理中心协作（governance 模块经 DocumentService 触达审核/删除状态）
    // =====================================================================

    @Override
    public PageData<ReviewQueueItem> listPendingReviews(int page, int size) {
        Long tenantId = currentTenantIdOrNull();
        // 待审队列 = review_status=PENDING_REVIEW 的未删文档（@TableLogic 自动过滤 del_flag=1）。
        IPage<Document> documentPage = documentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Document>()
                        .eq(tenantId != null, Document::getTenantId, tenantId)
                        .eq(Document::getReviewStatus, "PENDING_REVIEW")
                        .orderByDesc(Document::getUpdateTime));
        List<Document> documents = documentPage.getRecords();
        if (documents.isEmpty()) {
            return PageData.empty(page, size);
        }
        List<Long> documentIds = documents.stream().map(Document::getId).toList();
        // 一次拉取本批文档的全部审核留痕（管理页 size<=20，行数可控），
        // 内存组装「最近一次送审」与「累计意见数」，避免逐文档 N+1 查询。
        List<DocumentReview> history = documentReviewMapper.selectList(
                new LambdaQueryWrapper<DocumentReview>()
                        .in(DocumentReview::getDocumentId, documentIds)
                        .orderByDesc(DocumentReview::getId));
        Map<Long, DocumentReview> latestSubmitByDoc = new HashMap<>();
        Map<Long, Long> commentCountByDoc = new HashMap<>();
        for (DocumentReview review : history) {
            if ("SUBMIT".equals(review.getAction())) {
                // 结果已按 id 倒序，putIfAbsent 保留的首条即最新送审。
                latestSubmitByDoc.putIfAbsent(review.getDocumentId(), review);
            }
            if (StringUtils.hasText(review.getComment())) {
                // 意见数 = comment 非空的留痕行数（SUBMIT 行通常无意见，不计入）。
                commentCountByDoc.merge(review.getDocumentId(), 1L, Long::sum);
            }
        }
        List<ReviewQueueItem> items = documents.stream().map(document -> {
            // 无送审留痕的历史数据：降级用文档审计字段（提交人取送审动作发生者，退而取建档人）。
            DocumentReview submit = latestSubmitByDoc.get(document.getId());
            return new ReviewQueueItem(
                    submit != null ? submit.getId() : 0L,
                    document.getId(),
                    document.getKbId(),
                    document.getTitle(),
                    document.getSensitivity(),
                    submit != null ? submit.getActorUserId() : document.getCreateBy(),
                    submit != null ? submit.getCreateTime() : document.getCreateTime(),
                    commentCountByDoc.getOrDefault(document.getId(), 0L));
        }).toList();
        return PageData.of(items, documentPage.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void approveDocumentReview(long documentId, String comment) {
        // 审核状态机守卫：仅待审核文档可被通过（重复审批/已发布直接拒绝）。
        Document document = requireDocument(documentId);
        if (!"PENDING_REVIEW".equals(document.getReviewStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "文档不在待审核状态，无法通过");
        }
        // 通过即发布：review_status→PUBLISHED（进入在线索引的传播由检索链路按状态过滤）。
        document.setReviewStatus("PUBLISHED");
        documentMapper.updateById(document);
        // 追加 APPROVE 留痕（document_review 为追加写证据表，只插入不更新）。
        appendReviewHistory(document, "APPROVE", comment);
    }

    @Override
    @Transactional
    public void rejectDocumentReview(long documentId, String comment) {
        // 驳回必须给出理由（与前端「驳回必须填写审核意见」一致），供作者修改后重新提交。
        if (!StringUtils.hasText(comment)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "驳回必须填写审核意见");
        }
        Document document = requireDocument(documentId);
        if (!"PENDING_REVIEW".equals(document.getReviewStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "文档不在待审核状态，无法驳回");
        }
        document.setReviewStatus("REJECTED");
        documentMapper.updateById(document);
        // 追加 REJECT 留痕（携带驳回意见，作为作者修改的依据）。
        appendReviewHistory(document, "REJECT", comment);
    }

    @Override
    @Transactional
    public void withdrawDocumentFromReview(long documentId) {
        Document document = requireDocument(documentId);
        if (!"PENDING_REVIEW".equals(document.getReviewStatus())) {
            // 仅待审核文档可撤回（已发布/已驳回的走重新送审链路）。
            throw new ApiException(ErrorCode.CONFLICT, "仅待审核状态的文档可以撤回");
        }
        document.setReviewStatus("WITHDRAWN");
        documentMapper.updateById(document);
        // 追加 WITHDRAW 留痕（提交人主动撤回，审核队列即刻移除）。
        appendReviewHistory(document, "WITHDRAW", null);
    }

    @Override
    public DocumentGovernanceBrief documentGovernanceBrief(long documentId) {
        // 复用 requireDocument 的存在性 + 租户归属校验（deny-by-default）；
        // 软删后的文档按不存在处理 —— 治理侧快照在软删前已留档于 deletion_task.preview_json。
        Document document = requireDocument(documentId);
        return new DocumentGovernanceBrief(
                document.getId(),
                document.getKbId(),
                // 历史数据 tenant_id 为 0/null（未归属租户）时兜底默认租户 1（与 kbBrief 修复约定一致）。
                document.getTenantId() == null || document.getTenantId() <= 0
                        ? DEFAULT_TENANT_ID : document.getTenantId(),
                document.getTitle(),
                document.getFileName(),
                document.getSensitivity(),
                document.getLifecycleStatus(),
                document.getReviewStatus(),
                document.getCurrentVersionId(),
                document.getPolicyVersion());
    }

    @Override
    @Transactional
    public List<Long> softDeleteDocumentsByIds(long tenantId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        // 先圈定租户内未删文档（@TableLogic 过滤 del_flag=1，天然跳过已删/不存在/跨租户 id）。
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .in(Document::getId, documentIds)
                .select(Document::getId));
        if (documents.isEmpty()) {
            return List.of();
        }
        // 批量软删：lifecycle=DELETING + del_flag=1 两列一致（ck_document_del_flag 要求；
        // del_flag 为 @TableLogic 字段、实体更新不落 SET，故用 wrapper setSql 显式置 1）。
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .in(Document::getId, documents.stream().map(Document::getId).toList())
                .set(Document::getLifecycleStatus, LIFECYCLE_DELETING)
                .setSql("del_flag = 1"));
        // 返回实际软删的文档 id（对象存储原文与向量的物理清理由调用方异步执行）。
        return documents.stream().map(Document::getId).toList();
    }

    /** 追加审核留痕（document_review 追加写证据表，DDL REVOKE UPDATE/DELETE 保持不可变）。 */
    private void appendReviewHistory(Document document, String action, String comment) {
        DocumentReview review = new DocumentReview();
        review.setTenantId(document.getTenantId());
        review.setDocumentId(document.getId());
        review.setVersionId(document.getCurrentVersionId());
        review.setAction(action);
        review.setActorUserId(SecurityUtils.currentUserId());
        review.setComment(comment);
        review.setPolicyVersion(document.getPolicyVersion());
        documentReviewMapper.insert(review);
    }

    // =====================================================================
    // 摄取状态（供摄取调度器经 DocumentService 跨模块协作，避免直接依赖 document 持久化层）
    // =====================================================================

    @Override
    public DocumentIngestSource ingestSource(long versionId) {
        DocumentVersion version = documentVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "文档版本不存在");
        }
        Document document = documentMapper.selectById(version.getDocumentId());
        long kbId = document != null && document.getKbId() != null ? document.getKbId() : 0L;
        return new DocumentIngestSource(
                version.getDocumentId(), version.getId(), kbId,
                version.getVersionNo() == null ? 1 : version.getVersionNo(),
                version.getObjectKey());
    }

    @Override
    @Transactional
    public void updateIngestStatus(long versionId, String ingestStatus, Integer chunkCount, String errorCode) {
        DocumentVersion version = documentVersionMapper.selectById(versionId);
        if (version == null) {
            return;
        }
        version.setIngestStatus(ingestStatus);
        if (chunkCount != null) {
            version.setChunkCount(chunkCount);
        }
        version.setErrorCode(errorCode);
        if ("READY".equals(ingestStatus)) {
            version.setReadyAt(Instant.now());
        }
        documentVersionMapper.updateById(version);
    }

    // =====================================================================
    // ACL
    // =====================================================================

    @Override
    public List<AclEntryVo> getDocumentAcl(long documentId) {
        requireDocument(documentId);
        return documentAclMapper.selectList(new LambdaQueryWrapper<DocumentAcl>()
                        .eq(DocumentAcl::getDocumentId, documentId))
                .stream()
                .map(acl -> new AclEntryVo(acl.getId(), acl.getPrincipalType(),
                        acl.getPrincipalKey(), List.of(acl.getPermission())))
                .toList();
    }

    @Override
    @Transactional
    public List<AclEntryVo> setDocumentAcl(long documentId, AclSetDto request, String idempotencyKey) {
        requireDocument(documentId);
        // 覆盖式写入（白名单语义）：清空既有 ACL 后按条目逐权限落行。
        documentAclMapper.delete(new LambdaQueryWrapper<DocumentAcl>()
                .eq(DocumentAcl::getDocumentId, documentId));
        for (var entry : request.entries()) {
            for (String permission : entry.permissions()) {
                DocumentAcl acl = new DocumentAcl();
                acl.setTenantId(requireDocument(documentId).getTenantId());
                acl.setDocumentId(documentId);
                acl.setPrincipalType(entry.principalType());
                acl.setPrincipalKey(entry.principalName()); // 契约以 principalName 作为主体标识
                acl.setPermission(permission);
                documentAclMapper.insert(acl);
            }
        }
        return getDocumentAcl(documentId);
    }

    // =====================================================================
    // 收藏 / 标签
    // =====================================================================

    @Override
    @Transactional
    public boolean setFavorite(long documentId, boolean favorite) {
        requireDocument(documentId);
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getDocumentId, documentId);
        if (favorite) {
            // 幂等收藏：已收藏则跳过。
            if (userFavoriteMapper.selectCount(wrapper) == 0) {
                UserFavorite favoriteRow = new UserFavorite();
                favoriteRow.setUserId(userId);
                favoriteRow.setDocumentId(documentId);
                userFavoriteMapper.insert(favoriteRow);
            }
        } else {
            userFavoriteMapper.delete(wrapper);
        }
        return favorite;
    }

    @Override
    public PageData<FavoriteItemVo> listFavorites(int page, int size) {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        IPage<UserFavorite> favoritePage = userFavoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreateTime));
        if (favoritePage.getRecords().isEmpty()) {
            return PageData.empty(page, size);
        }
        // 批量回填文档标题/文件名与知识库名，避免 N+1。
        List<Document> documents = documentMapper.selectByIds(favoritePage.getRecords().stream()
                .map(UserFavorite::getDocumentId).toList());
        Map<Long, Document> byId = documents.stream().collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<Long, String> kbNames = kbService.kbNamesByIds(documents.stream().map(Document::getKbId).toList());
        List<FavoriteItemVo> items = favoritePage.getRecords().stream().map(favorite -> {
            Document document = byId.get(favorite.getDocumentId());
            return new FavoriteItemVo(favorite.getDocumentId(),
                    document != null ? document.getTitle() : "",
                    document != null ? document.getFileName() : "",
                    document != null ? kbNames.getOrDefault(document.getKbId(), "") : "",
                    favorite.getCreateTime() != null ? favorite.getCreateTime().toString() : "");
        }).toList();
        return PageData.of(items, favoritePage.getTotal(), page, size);
    }

    @Override
    public List<TagVo> listTags() {
        // 标签 + 文档计数：标签表一次 + 中间表一次，内存聚合（量级可控）。
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByDesc(Tag::getCreateTime));
        Map<Long, Long> counts = documentTagMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(DocumentTag::getTagId, Collectors.counting()));
        return tags.stream()
                .map(tag -> new TagVo(tag.getId(), tag.getName(), counts.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public TagVo createTag(String name, String idempotencyKey) {
        String tagName = name.trim();
        // 幂等创建：同名标签直接返回（标签为共享字典，不因重复调用产生重复行）。
        Tag existing = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, tagName)
                .last("LIMIT 1"));
        if (existing != null) {
            long count = documentTagMapper.selectCount(new LambdaQueryWrapper<DocumentTag>()
                    .eq(DocumentTag::getTagId, existing.getId()));
            return new TagVo(existing.getId(), existing.getName(), count);
        }
        Tag tag = new Tag();
        tag.setName(tagName);
        tagMapper.insert(tag);
        return new TagVo(tag.getId(), tag.getName(), 0L);
    }

    @Override
    @Transactional
    public void deleteTag(long tagId) {
        tagMapper.deleteById(tagId);
        // 级联清理文档-标签关联，避免残留引用。
        documentTagMapper.delete(new LambdaQueryWrapper<DocumentTag>().eq(DocumentTag::getTagId, tagId));
    }

    // =====================================================================
    // 预览 / 下载（权限校验 → 对象存储读原始字节流）
    // =====================================================================

    @Override
    public InputStream previewDocument(long documentId) {
        Document document = requireDocument(documentId);
        DocumentVersion version = requireCurrentVersion(document);
        // 权限：预览需 VIEW_CONTENT（认证授权 §5.2，策略层统一判定，deny-by-default）。
        // 未认证 / dev 场景无 userId 时放行（与列表/详情一致，不阻断本地开发）。
        Long userId = SecurityUtils.currentUserId();
        if (userId != null && userId > 0) {
            TenantId tenantId = requireTenantId(document.getTenantId());
            if (!accessPolicy.canViewContent(tenantId, new UserId(userId), documentId)) {
                throw new ApiException(ErrorCode.FORBIDDEN, "无预览权限（需要 VIEW_CONTENT）");
            }
        }
        return objectStore.get(requireTenantId(document.getTenantId()), version.getObjectKey())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理"));
    }

    @Override
    public InputStream downloadDocument(long documentId, Long versionId) {
        Document document = requireDocument(documentId);
        // versionId 指定版本；null 时取当前版本。
        DocumentVersion version;
        if (versionId != null) {
            version = documentVersionMapper.selectById(versionId);
            if (version == null || version.getDocumentId() == null
                    || version.getDocumentId() != documentId) {
                throw new ApiException(ErrorCode.NOT_FOUND, "目标版本不存在");
            }
        } else {
            version = requireCurrentVersion(document);
        }
        // 权限：下载需 DOWNLOAD_ORIGINAL（最高档位，策略层蕴含 VIEW_CONTENT→VIEW_EXCERPT）。
        Long userId = SecurityUtils.currentUserId();
        if (userId != null && userId > 0) {
            TenantId tenantId = requireTenantId(document.getTenantId());
            if (!accessPolicy.canDownloadOriginal(tenantId, new UserId(userId), documentId)) {
                throw new ApiException(ErrorCode.FORBIDDEN, "无下载权限（需要 DOWNLOAD_ORIGINAL）");
            }
        }
        return objectStore.get(requireTenantId(document.getTenantId()), version.getObjectKey())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理"));
    }

    // =====================================================================
    // 检索（统一委托 conversation 模块的同一搜索链路，避免双实现漂移）
    // =====================================================================

    /** 搜索会话服务（懒获取：SearchChatServiceImpl 构造期已注入 DocumentService，直接注入会成环）。 */
    @Autowired
    private ObjectProvider<com.ragkb.service.modules.conversation.service.SearchChatService> searchChatServiceProvider;

    @Override
    public Object getSearchExcerpt(String hitId) {
        // 历史遗留的重复声明：全文搜索唯一入口在 SearchController → SearchChatService，
        // 此处委托同一链路保持契约不破坏（FileExt 单数包装为列表透传）。
        return requireSearchChatService().getSearchExcerpt(hitId);
    }

    @Override
    public com.ragkb.service.common.api.CursorPageData<?> search(String keyword, List<Long> kbIds, String dateFrom,
                                                                 String dateTo, String fileExt, String sort,
                                                                 String cursor, int size) {
        // 委托同一搜索链路；本接口签名的 fileExt 为单值、无 fileExts 列表语义，
        // 包装为单元素列表；sort 当前由 Python 侧融合排序承担（本参数未消费）。
        return requireSearchChatService().search(keyword, kbIds,
                fileExt == null || fileExt.isBlank() ? List.of() : List.of(fileExt),
                dateFrom, dateTo, cursor, size);
    }

    /** 取搜索会话服务；同一 db.enabled 开关下必有实现 bean，缺失说明装配异常（快速失败）。 */
    private com.ragkb.service.modules.conversation.service.SearchChatService requireSearchChatService() {
        com.ragkb.service.modules.conversation.service.SearchChatService service = searchChatServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "搜索服务未装配");
        }
        return service;
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    /** 列表 → 摘要 VO：当前版本信息 + 知识库名批量回填（两次批量查询，无 N+1）。 */
    private List<DocumentSummaryVo> toSummaries(List<Document> documents) {
        List<Long> currentVersionIds = documents.stream()
                .map(Document::getCurrentVersionId).filter(Objects::nonNull).toList();
        Map<Long, DocumentVersion> versionById = currentVersionIds.isEmpty() ? Map.of()
                : documentVersionMapper.selectByIds(currentVersionIds).stream()
                .collect(Collectors.toMap(DocumentVersion::getId, Function.identity()));
        Map<Long, String> kbNames = kbService.kbNamesByIds(documents.stream().map(Document::getKbId).toList());
        return documents.stream().map(document -> {
            DocumentVersion version = document.getCurrentVersionId() != null
                    ? versionById.get(document.getCurrentVersionId()) : null;
            return new DocumentSummaryVo(
                    document.getId(), document.getKbId(),
                    kbNames.getOrDefault(document.getKbId(), ""),
                    document.getTitle(), document.getFileName(), document.getFileExt(),
                    document.getMimeType(), document.getSourceType(),
                    version != null && version.getFileSize() != null ? version.getFileSize() : 0L,
                    version != null && version.getVersionNo() != null ? version.getVersionNo() : 0,
                    version != null ? version.getIngestStatus() : null,
                    document.getReviewStatus(), document.getSensitivity(),
                    "", // ownerName：身份目录按 userId 查名待接入
                    version != null && version.getChunkCount() != null ? version.getChunkCount() : 0,
                    document.getUpdateTime() != null ? document.getUpdateTime().toString()
                            : document.getCreateTime().toString());
        }).toList();
    }

    /** 详情 VO 组装（含版本历史、标签、收藏状态）。 */
    private DocumentDetailVo toDetail(Document document, DocumentVersion version, List<String> tags,
                                      List<DocumentVersion> versions, boolean favorite) {
        String kbName = kbService.kbNamesByIds(List.of(document.getKbId()))
                .getOrDefault(document.getKbId(), "");
        return new DocumentDetailVo(
                document.getId(), document.getKbId(), kbName,
                document.getTitle(), document.getFileName(), document.getFileExt(),
                document.getMimeType(), document.getSourceType(),
                version.getFileSize() != null ? version.getFileSize() : 0L,
                version.getVersionNo() != null ? version.getVersionNo() : 0,
                version.getIngestStatus(), document.getReviewStatus(), document.getSensitivity(),
                "", // ownerName：身份目录按 userId 查名待接入
                version.getChunkCount() != null ? version.getChunkCount() : 0,
                document.getUpdateTime() != null ? document.getUpdateTime().toString()
                        : document.getCreateTime().toString(),
                versions.stream().map(this::toVersionVo).toList(),
                tags, favorite,
                null); // retryCount：解析失败次数由 parse_task 聚合，暂不展开
    }

    /** 版本实体 → 版本 VO（createdBy 需身份目录，当前置空）。 */
    private DocumentVersionVo toVersionVo(DocumentVersion version) {
        return new DocumentVersionVo(
                version.getVersionNo() != null ? version.getVersionNo() : 0,
                version.getFileSize() != null ? version.getFileSize() : 0L,
                version.getIngestStatus(), version.getSafetyStatus(),
                version.getChunkCount() != null ? version.getChunkCount() : 0,
                version.getCreateBy() != null ? String.valueOf(version.getCreateBy()) : "",
                version.getCreateTime() != null ? version.getCreateTime().toString() : "");
    }

    /** 文档必须存在（含租户归属校验，防止跨租户越权读取）。 */
    private Document requireDocument(long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        Long tenantId = currentTenantIdOrNull();
        if (tenantId != null && !tenantId.equals(document.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    /** 当前版本；无 current_version_id 时回退到最新版本。 */
    private DocumentVersion requireCurrentVersion(Document document) {
        if (document.getCurrentVersionId() != null) {
            DocumentVersion version = documentVersionMapper.selectById(document.getCurrentVersionId());
            if (version != null) {
                return version;
            }
        }
        DocumentVersion latest = documentVersionMapper.selectOne(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, document.getId())
                .orderByDesc(DocumentVersion::getVersionNo)
                .last("LIMIT 1"));
        if (latest == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "文档暂无版本");
        }
        return latest;
    }

    /** 文档的标签名列表（中间表 + 标签表各一次查询）。 */
    private List<String> tagNamesOf(long documentId) {
        List<Long> tagIds = documentTagMapper.selectList(new LambdaQueryWrapper<DocumentTag>()
                        .eq(DocumentTag::getDocumentId, documentId))
                .stream().map(DocumentTag::getTagId).toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectByIds(tagIds).stream().map(Tag::getName).toList();
    }

    /** 当前用户是否收藏该文档。 */
    private boolean isFavorite(long documentId) {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            return false;
        }
        return userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getDocumentId, documentId)) > 0;
    }

    /** 覆盖式替换文档标签（先清空关联，再按名称复用/新建标签）。 */
    private void replaceTags(long documentId, Collection<String> tagNames) {
        documentTagMapper.delete(new LambdaQueryWrapper<DocumentTag>()
                .eq(DocumentTag::getDocumentId, documentId));
        for (String name : tagNames) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, name.trim()).last("LIMIT 1"));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name.trim());
                tagMapper.insert(tag);
            }
            DocumentTag link = new DocumentTag();
            link.setDocumentId(documentId);
            link.setTagId(tag.getId());
            documentTagMapper.insert(link);
        }
    }

    /** 已上传分片号列表（幂等续传提示用）。 */
    private List<Integer> existingUploadedParts(UploadSessionStore.Session session) {
        return List.copyOf(uploadSessionStore.uploadedParts(session.uploadId));
    }

    /** 对象存储 key：{tenant}/{yyyy}/{MM}/{uploadId}-{安全文件名}，天然按租户/时间分桶。 */
    private String buildObjectKey(UploadSessionStore.Session session) {
        long tenantId = session.tenantId > 0 ? session.tenantId : DEFAULT_TENANT_ID;
        LocalDate date = LocalDate.now(ZoneId.of("UTC"));
        String safeName = session.fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return tenantId + "/" + date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + session.uploadId + "-" + safeName;
    }

    /** SHA-256 十六进制小写摘要（document_version.content_sha256 要求 64 位小写十六进制）。 */
    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 扩展名（小写，无点）。 */
    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    /** 去掉扩展名的文件名（作为默认标题）。 */
    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 常见扩展名 → MIME 类型（未收录一律 octet-stream）。 */
    private String mimeTypeOf(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "html" -> "text/html";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    /** 当前 JWT 主体的租户 id；dev/API Key/未认证返回 null（此时不强制租户过滤）。 */
    private Long currentTenantIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal
                && principal.tenantId() > 0) {
            return principal.tenantId();
        }
        return null;
    }

    /** 默认租户 id：未归属租户的知识库/文档（tenantId 为 0 或 null）兜底使用，保证流程不中断。 */
    private static final long DEFAULT_TENANT_ID = 1L;

    /**
     * 构造 {@link TenantId} 值对象；tenantId 为 null 或 ≤0 时兜底为默认租户 1
     * （dev/种子数据场景，知识库未归属租户），保证上传/摄取流程不中断。
     */
    private TenantId requireTenantId(Long tenantId) {
        long resolved = (tenantId == null || tenantId <= 0) ? DEFAULT_TENANT_ID : tenantId;
        return new TenantId(resolved);
    }
}
