package com.ragkb.service.modules.document.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.document.vo.AclEntryVo;
import com.ragkb.service.modules.document.dto.AclSetDto;
import com.ragkb.service.modules.document.dto.DeletionDto;
import com.ragkb.service.modules.document.vo.DocumentDetailVo;
import com.ragkb.service.modules.document.vo.DocumentSummaryVo;
import com.ragkb.service.modules.document.vo.DocumentVersionVo;
import com.ragkb.service.modules.document.vo.FavoriteItemVo;
import com.ragkb.service.modules.document.dto.FavoriteDto;
import com.ragkb.service.modules.document.dto.RollbackVersionDto;
import com.ragkb.service.modules.document.vo.TagVo;
import com.ragkb.service.modules.document.dto.CreateTagDto;
import com.ragkb.service.modules.document.dto.UpdateDocumentMetadataDto;
import com.ragkb.service.modules.document.dto.UploadInitDto;
import com.ragkb.service.modules.document.vo.UploadInitResponseVo;
import com.ragkb.service.modules.document.service.DocumentService;
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
import org.springframework.http.MediaType;
import java.io.InputStream;
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
    public ApiResponse<PageData<DocumentSummaryVo>> listDocuments(
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
    public ApiResponse<PageData<DocumentSummaryVo>> listAllDocuments(
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
    public ApiResponse<DocumentDetailVo> getDocument(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.getDocument(documentId));
    }

    @GetMapping("/api/v1/documents/{documentId}/versions")
    public ApiResponse<List<DocumentVersionVo>> listDocumentVersions(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.listDocumentVersions(documentId));
    }

    /**
     * 在线预览：返回文档当前版本的原始字节流（需 VIEW_CONTENT 权限）。
     * PDF/图片等浏览器可直接渲染的格式走 inline；其余由前端按 Content-Type 处理。
     */
    @GetMapping("/api/v1/documents/{documentId}/preview")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> previewDocument(
            @PathVariable long documentId) {
        InputStream stream = documentService.previewDocument(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "inline")
                .body(new org.springframework.core.io.InputStreamResource(stream));
    }

    /**
     * 下载原文：返回指定版本（versionId=null 时取当前版本）的原始字节流
     * （需 DOWNLOAD_ORIGINAL 权限），Content-Disposition=attachment 触发浏览器下载。
     */
    @GetMapping("/api/v1/documents/{documentId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadDocument(
            @PathVariable long documentId,
            @RequestParam(required = false) Long versionId) {
        InputStream stream = documentService.downloadDocument(documentId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment")
                .body(new org.springframework.core.io.InputStreamResource(stream));
    }

    /** 更新文档元数据（产品契约所需；OpenAPI 草案仅定义 schema 值更新，此处为文档基础字段）。 */
    @PatchMapping("/api/v1/documents/{documentId}")
    public ApiResponse<DocumentDetailVo> updateDocumentMetadata(
            @PathVariable long documentId,
            @Valid @RequestBody UpdateDocumentMetadataDto request) {
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
    public ApiResponse<DocumentDetailVo> rollbackVersion(
            @PathVariable long documentId,
            @Valid @RequestBody RollbackVersionDto request) {
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
            @Valid @RequestBody DeletionDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.deleteDocument(documentId, request, idempotencyKey));
    }

    @GetMapping("/api/v1/documents/{documentId}/acl")
    public ApiResponse<List<AclEntryVo>> getDocumentAcl(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.getDocumentAcl(documentId));
    }

    @PutMapping("/api/v1/documents/{documentId}/acl")
    public ApiResponse<List<AclEntryVo>> setDocumentAcl(
            @PathVariable long documentId,
            @Valid @RequestBody AclSetDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.setDocumentAcl(documentId, request, idempotencyKey));
    }

    @PostMapping("/api/v1/upload/init")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadInitResponseVo> initUpload(
            @Valid @RequestBody UploadInitDto request,
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
    public ApiResponse<List<TagVo>> listTags() {
        return ApiResponse.ok(documentService.listTags());
    }

    @PostMapping("/api/v1/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TagVo> createTag(
            @Valid @RequestBody CreateTagDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.createTag(request.name(), idempotencyKey));
    }

    @DeleteMapping("/api/v1/tags/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable long tagId) {
        documentService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/favorites")
    public ApiResponse<PageData<FavoriteItemVo>> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(documentService.listFavorites(page, size));
    }

    @PostMapping("/api/v1/favorites")
    public ResponseEntity<Void> addFavorite(@Valid @RequestBody FavoriteDto request) {
        documentService.setFavorite(request.documentId(), true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/favorites")
    public ResponseEntity<Void> removeFavorite(@Valid @RequestBody FavoriteDto request) {
        documentService.setFavorite(request.documentId(), false);
        return ResponseEntity.noContent().build();
    }
}
