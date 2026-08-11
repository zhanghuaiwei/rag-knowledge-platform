package com.ragkb.service.application;

import com.ragkb.service.common.CursorPageData;
import com.ragkb.service.common.PageData;
import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.DocumentDtos.AclEntry;
import com.ragkb.service.interfaces.dto.DocumentDtos.AclSetRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.DeletionRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.DocumentDetail;
import com.ragkb.service.interfaces.dto.DocumentDtos.DocumentSummary;
import com.ragkb.service.interfaces.dto.DocumentDtos.DocumentVersion;
import com.ragkb.service.interfaces.dto.DocumentDtos.FavoriteItem;
import com.ragkb.service.interfaces.dto.DocumentDtos.RollbackVersionRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.Tag;
import com.ragkb.service.interfaces.dto.DocumentDtos.UpdateDocumentMetadataRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.UploadInitRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.UploadInitResponse;

import java.util.List;

/**
 * 文档域用例（实现点由人工完成；摄取/解析/嵌入落在 rag-engine，见 RagEnginePort）。
 */
public interface DocumentService {

    /** kbId 为 null 时列出授权范围内全部文档。 */
    PageData<DocumentSummary> listDocuments(
            Long kbId, String reviewStatus, String ingestStatus, String sensitivity,
            String keyword, Long tagId, int page, int size);

    DocumentDetail getDocument(long documentId);

    List<DocumentVersion> listDocumentVersions(long documentId);

    UploadInitResponse initUpload(UploadInitRequest request, String idempotencyKey);

    /** 分片上传：content 为原始字节流。 */
    void uploadPart(String uploadId, int partNumber, byte[] content);

    Task completeUpload(String uploadId, String idempotencyKey);

    DocumentDetail updateDocumentMetadata(long documentId, UpdateDocumentMetadataRequest request);

    /** 文档软删除：提交删除申请，异步处置。 */
    Task deleteDocument(long documentId, DeletionRequest request, String idempotencyKey);

    Task reparseDocument(long documentId, String idempotencyKey);

    DocumentDetail rollbackVersion(long documentId, RollbackVersionRequest request);

    void submitForReview(long documentId);

    void disableDocument(long documentId);

    void enableDocument(long documentId);

    List<AclEntry> getDocumentAcl(long documentId);

    /** 覆盖式写入；返回写入后的完整 ACL 列表。 */
    List<AclEntry> setDocumentAcl(long documentId, AclSetRequest request, String idempotencyKey);

    boolean setFavorite(long documentId, boolean favorite);

    PageData<FavoriteItem> listFavorites(int page, int size);

    List<Tag> listTags();

    Tag createTag(String name, String idempotencyKey);

    void deleteTag(long tagId);

    /** 搜索命中按当前权限重新授权后的摘录。 */
    Object getSearchExcerpt(String hitId);

    /** 全文搜索（cursor 游标分页）。 */
    CursorPageData<?> search(String keyword, List<Long> kbIds, String dateFrom, String dateTo,
                             String fileExt, String sort, String cursor, int size);
}
