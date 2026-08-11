package com.ragkb.service.interfaces;

import com.ragkb.service.application.DocumentService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.common.PageData;
import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.DocumentDtos.AclEntry;
import com.ragkb.service.interfaces.dto.DocumentDtos.AclSetRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.DeletionRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.DocumentDetail;
import com.ragkb.service.interfaces.dto.DocumentDtos.DocumentSummary;
import com.ragkb.service.interfaces.dto.DocumentDtos.DocumentVersion;
import com.ragkb.service.interfaces.dto.DocumentDtos.FavoriteItem;
import com.ragkb.service.interfaces.dto.DocumentDtos.FavoriteRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.RollbackVersionRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.Tag;
import com.ragkb.service.interfaces.dto.DocumentDtos.CreateTagRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.UpdateDocumentMetadataRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.UploadInitRequest;
import com.ragkb.service.interfaces.dto.DocumentDtos.UploadInitResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档接口入口（文档 / 版本 / 上传 / 预览 / ACL / 删除 / 标签 / 收藏）。业务实现见 {@link DocumentService}。
 */
@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/v1/kbs/{kbId}/documents")
    public ApiResponse<PageData<DocumentSummary>> listDocuments(
            @PathVariable long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String ingestStatus,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long tagId) {
        return ApiResponse.ok(documentService.listDocuments(
                kbId, reviewStatus, ingestStatus, sensitivity, keyword, tagId, page, size));
    }

    /** 全部文档列表（产品契约所需；前端文档库页可不带 kb 过滤）。 */
    @GetMapping("/api/v1/documents")
    public ApiResponse<PageData<DocumentSummary>> listAllDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String ingestStatus,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long tagId) {
        return ApiResponse.ok(documentService.listDocuments(
                null, reviewStatus, ingestStatus, sensitivity, keyword, tagId, page, size));
    }

    @GetMapping("/api/v1/documents/{documentId}")
    public ApiResponse<DocumentDetail> getDocument(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.getDocument(documentId));
    }

    @GetMapping("/api/v1/documents/{documentId}/versions")
    public ApiResponse<List<DocumentVersion>> listDocumentVersions(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.listDocumentVersions(documentId));
    }

    /** 更新文档元数据（产品契约所需；OpenAPI 草案仅定义 schema 值更新，此处为文档基础字段）。 */
    @PatchMapping("/api/v1/documents/{documentId}")
    public ApiResponse<DocumentDetail> updateDocumentMetadata(
            @PathVariable long documentId,
            @Valid @RequestBody UpdateDocumentMetadataRequest request) {
        return ApiResponse.ok(documentService.updateDocumentMetadata(documentId, request));
    }

    @PostMapping("/api/v1/documents/{documentId}/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> submitForReview(@PathVariable long documentId) {
        documentService.submitForReview(documentId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/documents/{documentId}/reparse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> reparseDocument(
            @PathVariable long documentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.reparseDocument(documentId, idempotencyKey));
    }

    /** 回滚版本（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PostMapping("/api/v1/documents/{documentId}/rollback")
    public ApiResponse<DocumentDetail> rollbackVersion(
            @PathVariable long documentId,
            @Valid @RequestBody RollbackVersionRequest request) {
        return ApiResponse.ok(documentService.rollbackVersion(documentId, request));
    }

    @PostMapping("/api/v1/documents/{documentId}/disable")
    public ResponseEntity<Void> disableDocument(@PathVariable long documentId) {
        documentService.disableDocument(documentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/documents/{documentId}/enable")
    public ResponseEntity<Void> enableDocument(@PathVariable long documentId) {
        documentService.enableDocument(documentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/documents/{documentId}/deletion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> deleteDocument(
            @PathVariable long documentId,
            @Valid @RequestBody DeletionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.deleteDocument(documentId, request, idempotencyKey));
    }

    @GetMapping("/api/v1/documents/{documentId}/acl")
    public ApiResponse<List<AclEntry>> getDocumentAcl(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.getDocumentAcl(documentId));
    }

    @PutMapping("/api/v1/documents/{documentId}/acl")
    public ApiResponse<List<AclEntry>> setDocumentAcl(
            @PathVariable long documentId,
            @Valid @RequestBody AclSetRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.setDocumentAcl(documentId, request, idempotencyKey));
    }

    @PostMapping("/api/v1/upload/init")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadInitResponse> initUpload(
            @Valid @RequestBody UploadInitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.initUpload(request, idempotencyKey));
    }

    @PutMapping("/api/v1/upload/{uploadId}/parts/{partNumber}")
    public ResponseEntity<Void> uploadPart(
            @PathVariable String uploadId,
            @PathVariable int partNumber,
            @RequestBody byte[] content) {
        documentService.uploadPart(uploadId, partNumber, content);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/upload/{uploadId}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> completeUpload(
            @PathVariable String uploadId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.completeUpload(uploadId, idempotencyKey));
    }

    // ---- 标签 / 收藏 ----

    @GetMapping("/api/v1/tags")
    public ApiResponse<List<Tag>> listTags() {
        return ApiResponse.ok(documentService.listTags());
    }

    @PostMapping("/api/v1/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Tag> createTag(
            @Valid @RequestBody CreateTagRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.createTag(request.name(), idempotencyKey));
    }

    @DeleteMapping("/api/v1/tags/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable long tagId) {
        documentService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/favorites")
    public ApiResponse<PageData<FavoriteItem>> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(documentService.listFavorites(page, size));
    }

    @PostMapping("/api/v1/favorites")
    public ResponseEntity<Void> addFavorite(@Valid @RequestBody FavoriteRequest request) {
        documentService.setFavorite(request.documentId(), true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/favorites")
    public ResponseEntity<Void> removeFavorite(@Valid @RequestBody FavoriteRequest request) {
        documentService.setFavorite(request.documentId(), false);
        return ResponseEntity.noContent().build();
    }
}
