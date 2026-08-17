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
import com.ragkb.service.modules.document.dto.RollbackVersionDto;
import com.ragkb.service.modules.document.dto.UpdateDocumentMetadataDto;
import com.ragkb.service.modules.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.List;

// 装配条件：文档域依赖数据库持久化（ragkb.db.enabled=true），scaffold 模式下端点整体下线
// （与 conversation/identity 模块的条件装配约定一致，避免无数据库时上下文装配失败）。
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/kbs/{kbId}/documents")
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

    @GetMapping("/documents")
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

    @GetMapping("/documents/{documentId}")
    public ApiResponse<DocumentDetailVo> getDocument(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.getDocument(documentId));
    }

    @GetMapping("/documents/{documentId}/versions")
    public ApiResponse<List<DocumentVersionVo>> listDocumentVersions(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.listDocumentVersions(documentId));
    }

    @GetMapping("/documents/{documentId}/preview")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> previewDocument(
            @PathVariable long documentId) {
        InputStream stream = documentService.previewDocument(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "inline")
                .body(new org.springframework.core.io.InputStreamResource(stream));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadDocument(
            @PathVariable long documentId,
            @RequestParam(required = false) Long versionId) {
        InputStream stream = documentService.downloadDocument(documentId, versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment")
                .body(new org.springframework.core.io.InputStreamResource(stream));
    }

    @PatchMapping("/documents/{documentId}")
    public ApiResponse<DocumentDetailVo> updateDocumentMetadata(
            @PathVariable long documentId,
            @Valid @RequestBody UpdateDocumentMetadataDto request) {
        return ApiResponse.ok(documentService.updateDocumentMetadata(documentId, request));
    }

    @PostMapping("/documents/{documentId}/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> submitForReview(@PathVariable long documentId) {
        documentService.submitForReview(documentId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/documents/{documentId}/reparse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> reparseDocument(
            @PathVariable long documentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.reparseDocument(documentId, idempotencyKey));
    }

    @PostMapping("/documents/{documentId}/rollback")
    public ApiResponse<DocumentDetailVo> rollbackVersion(
            @PathVariable long documentId,
            @Valid @RequestBody RollbackVersionDto request) {
        return ApiResponse.ok(documentService.rollbackVersion(documentId, request));
    }

    @PostMapping("/documents/{documentId}/disable")
    public ResponseEntity<Void> disableDocument(@PathVariable long documentId) {
        documentService.disableDocument(documentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/documents/{documentId}/enable")
    public ResponseEntity<Void> enableDocument(@PathVariable long documentId) {
        documentService.enableDocument(documentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/documents/{documentId}/deletion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> deleteDocument(
            @PathVariable long documentId,
            @Valid @RequestBody DeletionDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.deleteDocument(documentId, request, idempotencyKey));
    }

    @GetMapping("/documents/{documentId}/acl")
    public ApiResponse<List<AclEntryVo>> getDocumentAcl(@PathVariable long documentId) {
        return ApiResponse.ok(documentService.getDocumentAcl(documentId));
    }

    @PutMapping("/documents/{documentId}/acl")
    public ApiResponse<List<AclEntryVo>> setDocumentAcl(
            @PathVariable long documentId,
            @Valid @RequestBody AclSetDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.setDocumentAcl(documentId, request, idempotencyKey));
    }
}
