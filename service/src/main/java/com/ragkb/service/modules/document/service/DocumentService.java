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

import java.util.List;

/**
 * 文档域用例（实现点由人工完成；摄取/解析/嵌入落在 rag-engine，见 RagEnginePort）。
 */
public interface DocumentService {

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

    /** 全文搜索（cursor 游标分页）。 */
    CursorPageData<?> search(String keyword, List<Long> kbIds, String dateFrom, String dateTo,
                             String fileExt, String sort, String cursor, int size);
}
