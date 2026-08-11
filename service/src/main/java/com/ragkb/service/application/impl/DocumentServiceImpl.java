package com.ragkb.service.application.impl;

import com.ragkb.service.application.DocumentService;
import com.ragkb.service.application.NotYetImplemented;
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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档用例桩实现（实现点由人工替换；摄取/解析落在 rag-engine）。
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    @Override
    public PageData<DocumentSummary> listDocuments(Long kbId, String reviewStatus, String ingestStatus,
                                                   String sensitivity, String keyword, Long tagId, int page, int size) {
        return NotYetImplemented.stub("DocumentService#listDocuments");
    }

    @Override
    public DocumentDetail getDocument(long documentId) {
        return NotYetImplemented.stub("DocumentService#getDocument");
    }

    @Override
    public List<DocumentVersion> listDocumentVersions(long documentId) {
        return NotYetImplemented.stub("DocumentService#listDocumentVersions");
    }

    @Override
    public UploadInitResponse initUpload(UploadInitRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("DocumentService#initUpload");
    }

    @Override
    public void uploadPart(String uploadId, int partNumber, byte[] content) {
        NotYetImplemented.stub("DocumentService#uploadPart");
    }

    @Override
    public Task completeUpload(String uploadId, String idempotencyKey) {
        return NotYetImplemented.stub("DocumentService#completeUpload");
    }

    @Override
    public DocumentDetail updateDocumentMetadata(long documentId, UpdateDocumentMetadataRequest request) {
        return NotYetImplemented.stub("DocumentService#updateDocumentMetadata");
    }

    @Override
    public Task deleteDocument(long documentId, DeletionRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("DocumentService#deleteDocument");
    }

    @Override
    public Task reparseDocument(long documentId, String idempotencyKey) {
        return NotYetImplemented.stub("DocumentService#reparseDocument");
    }

    @Override
    public DocumentDetail rollbackVersion(long documentId, RollbackVersionRequest request) {
        return NotYetImplemented.stub("DocumentService#rollbackVersion");
    }

    @Override
    public void submitForReview(long documentId) {
        NotYetImplemented.stub("DocumentService#submitForReview");
    }

    @Override
    public void disableDocument(long documentId) {
        NotYetImplemented.stub("DocumentService#disableDocument");
    }

    @Override
    public void enableDocument(long documentId) {
        NotYetImplemented.stub("DocumentService#enableDocument");
    }

    @Override
    public List<AclEntry> getDocumentAcl(long documentId) {
        return NotYetImplemented.stub("DocumentService#getDocumentAcl");
    }

    @Override
    public List<AclEntry> setDocumentAcl(long documentId, AclSetRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("DocumentService#setDocumentAcl");
    }

    @Override
    public boolean setFavorite(long documentId, boolean favorite) {
        return NotYetImplemented.stub("DocumentService#setFavorite");
    }

    @Override
    public PageData<FavoriteItem> listFavorites(int page, int size) {
        return NotYetImplemented.stub("DocumentService#listFavorites");
    }

    @Override
    public List<Tag> listTags() {
        return NotYetImplemented.stub("DocumentService#listTags");
    }

    @Override
    public Tag createTag(String name, String idempotencyKey) {
        return NotYetImplemented.stub("DocumentService#createTag");
    }

    @Override
    public void deleteTag(long tagId) {
        NotYetImplemented.stub("DocumentService#deleteTag");
    }

    @Override
    public Object getSearchExcerpt(String hitId) {
        return NotYetImplemented.stub("DocumentService#getSearchExcerpt");
    }

    @Override
    public CursorPageData<?> search(String keyword, List<Long> kbIds, String dateFrom, String dateTo,
                                    String fileExt, String sort, String cursor, int size) {
        return NotYetImplemented.stub("DocumentService#search");
    }
}
