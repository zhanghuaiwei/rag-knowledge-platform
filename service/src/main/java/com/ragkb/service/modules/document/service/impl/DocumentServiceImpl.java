package com.ragkb.service.modules.document.service.impl;

import com.ragkb.service.util.TodoSupport;
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
import com.ragkb.service.modules.document.service.DocumentService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档用例桩实现（实现点由人工替换；摄取/解析落在 rag-engine）。
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    @Override
    public PageData<DocumentSummaryVo> listDocuments(Long kbId, String reviewStatus, String ingestStatus,
                                                   String sensitivity, String keyword, Long tagId, int page, int size) {
        return TodoSupport.notImplemented("DocumentService#listDocuments");
    }

    @Override
    public DocumentDetailVo getDocument(long documentId) {
        return TodoSupport.notImplemented("DocumentService#getDocument");
    }

    @Override
    public List<DocumentVersionVo> listDocumentVersions(long documentId) {
        return TodoSupport.notImplemented("DocumentService#listDocumentVersions");
    }

    @Override
    public UploadInitResponseVo initUpload(UploadInitDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("DocumentService#initUpload");
    }

    @Override
    public void uploadPart(String uploadId, int partNumber, byte[] content) {
        TodoSupport.notImplemented("DocumentService#uploadPart");
    }

    @Override
    public Task completeUpload(String uploadId, String idempotencyKey) {
        return TodoSupport.notImplemented("DocumentService#completeUpload");
    }

    @Override
    public DocumentDetailVo updateDocumentMetadata(long documentId, UpdateDocumentMetadataDto request) {
        return TodoSupport.notImplemented("DocumentService#updateDocumentMetadata");
    }

    @Override
    public Task deleteDocument(long documentId, DeletionDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("DocumentService#deleteDocument");
    }

    @Override
    public Task reparseDocument(long documentId, String idempotencyKey) {
        return TodoSupport.notImplemented("DocumentService#reparseDocument");
    }

    @Override
    public DocumentDetailVo rollbackVersion(long documentId, RollbackVersionDto request) {
        return TodoSupport.notImplemented("DocumentService#rollbackVersion");
    }

    @Override
    public void submitForReview(long documentId) {
        TodoSupport.notImplemented("DocumentService#submitForReview");
    }

    @Override
    public void disableDocument(long documentId) {
        TodoSupport.notImplemented("DocumentService#disableDocument");
    }

    @Override
    public void enableDocument(long documentId) {
        TodoSupport.notImplemented("DocumentService#enableDocument");
    }

    @Override
    public List<AclEntryVo> getDocumentAcl(long documentId) {
        return TodoSupport.notImplemented("DocumentService#getDocumentAcl");
    }

    @Override
    public List<AclEntryVo> setDocumentAcl(long documentId, AclSetDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("DocumentService#setDocumentAcl");
    }

    @Override
    public boolean setFavorite(long documentId, boolean favorite) {
        return TodoSupport.notImplemented("DocumentService#setFavorite");
    }

    @Override
    public PageData<FavoriteItemVo> listFavorites(int page, int size) {
        return TodoSupport.notImplemented("DocumentService#listFavorites");
    }

    @Override
    public List<TagVo> listTags() {
        return TodoSupport.notImplemented("DocumentService#listTags");
    }

    @Override
    public TagVo createTag(String name, String idempotencyKey) {
        return TodoSupport.notImplemented("DocumentService#createTag");
    }

    @Override
    public void deleteTag(long tagId) {
        TodoSupport.notImplemented("DocumentService#deleteTag");
    }

    @Override
    public Object getSearchExcerpt(String hitId) {
        return TodoSupport.notImplemented("DocumentService#getSearchExcerpt");
    }

    @Override
    public CursorPageData<?> search(String keyword, List<Long> kbIds, String dateFrom, String dateTo,
                                    String fileExt, String sort, String cursor, int size) {
        return TodoSupport.notImplemented("DocumentService#search");
    }
}
