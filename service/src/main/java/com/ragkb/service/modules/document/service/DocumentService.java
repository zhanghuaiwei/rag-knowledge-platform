package com.ragkb.service.modules.document.service;

import com.ragkb.service.common.api.CursorPageData;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.document.vo.AclEntryVo;
import com.ragkb.service.modules.document.dto.AclSetDto;
import com.ragkb.service.modules.document.dto.DeletionDto;
import com.ragkb.service.modules.document.vo.DocumentDetailVo;
import com.ragkb.service.modules.document.vo.DocumentSummaryVo;
import com.ragkb.service.modules.document.vo.DocumentVersionVo;
import com.ragkb.service.modules.document.vo.FavoriteItemVo;
import com.ragkb.service.modules.document.dto.RollbackVersionDto;
import com.ragkb.service.modules.document.vo.TagVo;
import com.ragkb.service.modules.document.dto.UpdateDocumentMetadataDto;
import com.ragkb.service.modules.document.dto.UploadInitDto;
import com.ragkb.service.modules.document.vo.UploadInitResponseVo;

import java.io.InputStream;
import java.util.List;

/**
 * 文档域用例（实现点由人工完成；摄取/解析/嵌入落在 rag-engine，见 RagEnginePort）。
 */
public interface DocumentService {

    /** 文档版本的摄取来源（供摄取调度器投递 rag-engine 使用；objectKey 为对象存储引用）。 */
    record DocumentIngestSource(long documentId, long versionId, long kbId,
                                long versionNo, String objectKey) {
    }

    /** 读取文档版本的摄取来源（objectKey/版本号/kbId）；不存在抛 {@code NOT_FOUND}。 */
    DocumentIngestSource ingestSource(long versionId);

    /** 回写文档版本摄取状态：READY→chunkCount+ready_at；FAILED→error_code；其余仅推进状态。 */
    void updateIngestStatus(long versionId, String ingestStatus, Integer chunkCount, String errorCode);


    /** kbId 为 null 时列出授权范围内全部文档。 */
    PageData<DocumentSummaryVo> listDocuments(
            Long kbId, String reviewStatus, String ingestStatus, String sensitivity,
            String keyword, Long tagId, int page, int size);

    DocumentDetailVo getDocument(long documentId);

    List<DocumentVersionVo> listDocumentVersions(long documentId);

    UploadInitResponseVo initUpload(UploadInitDto request, String idempotencyKey);

    /** 分片上传：content 为原始字节流。 */
    void uploadPart(String uploadId, int partNumber, byte[] content);

    Task completeUpload(String uploadId, String idempotencyKey);

    DocumentDetailVo updateDocumentMetadata(long documentId, UpdateDocumentMetadataDto request);

    /** 文档软删除：提交删除申请，异步处置。 */
    Task deleteDocument(long documentId, DeletionDto request, String idempotencyKey);

    /**
     * 按知识库批量软删文档（知识库删除的级联步骤，由 knowledge 模块经本 Service 调用）：
     * 将该库下全部未删文档标记 lifecycle=DELETING + del_flag=1，
     * 返回受影响的文档 id 列表（供调用方异步清理 rag-engine 向量）。
     */
    List<Long> softDeleteDocumentsByKb(long tenantId, long kbId);

    Task reparseDocument(long documentId, String idempotencyKey);

    DocumentDetailVo rollbackVersion(long documentId, RollbackVersionDto request);

    void submitForReview(long documentId);

    void disableDocument(long documentId);

    void enableDocument(long documentId);

    List<AclEntryVo> getDocumentAcl(long documentId);

    /** 覆盖式写入；返回写入后的完整 ACL 列表。 */
    List<AclEntryVo> setDocumentAcl(long documentId, AclSetDto request, String idempotencyKey);

    boolean setFavorite(long documentId, boolean favorite);

    PageData<FavoriteItemVo> listFavorites(int page, int size);

    List<TagVo> listTags();

    TagVo createTag(String name, String idempotencyKey);

    void deleteTag(long tagId);

    /** 搜索命中按当前权限重新授权后的摘录。 */
    Object getSearchExcerpt(String hitId);

    /**
     * 在线预览：返回文档当前版本的原始字节流（需 VIEW_CONTENT 权限）。
     * 调用方（Controller）负责设置 Content-Type / Content-Disposition。
     */
    InputStream previewDocument(long documentId);

    /**
     * 下载原文：返回指定版本（versionId=null 时取当前版本）的原始字节流
     * （需 DOWNLOAD_ORIGINAL 权限）。调用方负责设置 attachment 响应头。
     */
    InputStream downloadDocument(long documentId, Long versionId);

    /** 全文搜索（cursor 游标分页）。 */
    CursorPageData<?> search(String keyword, List<Long> kbIds, String dateFrom, String dateTo,
                             String fileExt, String sort, String cursor, int size);

    // =====================================================================
    // 治理中心协作（governance 模块经本 Service 触达 document 领域的审核与删除状态，
    // 避免跨模块直接依赖 document 持久化层 —— PackageStructureTest 红线）
    // =====================================================================

    /**
     * 审核队列条目快照：待审文档 + 最近一次送审留痕（governance 组装队列视图的原始数据）。
     *
     * @param reviewId     最近一次 SUBMIT 留痕的 document_review.id（无留痕的历史数据为 0）
     * @param commentCount 该文档累计审核意见条数（document_review.comment 非空行数）
     */
    record ReviewQueueItem(long reviewId, long documentId, long kbId, String title, String sensitivity,
                           Long submitterId, java.time.Instant submittedAt, long commentCount) {
    }

    /** 分页列出待审核文档（review_status=PENDING_REVIEW，tenant 过滤 + del_flag=0）。 */
    PageData<ReviewQueueItem> listPendingReviews(int page, int size);

    /** 审核通过：review_status→PUBLISHED 并追加 APPROVE 留痕；非待审状态抛 CONFLICT。 */
    void approveDocumentReview(long documentId, String comment);

    /** 审核驳回：review_status→REJECTED 并追加 REJECT 留痕；意见为空抛 BAD_REQUEST。 */
    void rejectDocumentReview(long documentId, String comment);

    /** 撤回送审：review_status→WITHDRAWN 并追加 WITHDRAW 留痕；非待审状态抛 CONFLICT。 */
    void withdrawDocumentFromReview(long documentId);

    /**
     * 文档治理快照（删除审批展示与留档用）：仅未软删文档可读，
     * 不存在或跨租户按 NOT_FOUND 拒绝（deny-by-default）。
     */
    record DocumentGovernanceBrief(long documentId, long kbId, long tenantId, String title,
                                   String fileName, String sensitivity, String lifecycleStatus,
                                   String reviewStatus, Long currentVersionId, Long policyVersion) {
    }

    /** 读取文档治理快照（deleteKb 级联与治理删除审批共用的文档侧只读视图）。 */
    DocumentGovernanceBrief documentGovernanceBrief(long documentId);

    /**
     * 按 id 批量软删文档（治理删除审批的执行步骤）：lifecycle=DELETING + del_flag=1，
     * 返回实际软删成功的文档 id（已删/不存在/跨租户的 id 被跳过）。
     */
    List<Long> softDeleteDocumentsByIds(long tenantId, List<Long> documentIds);
}
