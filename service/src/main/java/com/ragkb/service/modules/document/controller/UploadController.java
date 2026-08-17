package com.ragkb.service.modules.document.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.document.dto.UploadInitDto;
import com.ragkb.service.modules.document.vo.UploadInitResponseVo;
import com.ragkb.service.modules.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 装配条件：上传链路依赖数据库持久化与摄取队列（ragkb.db.enabled=true），scaffold 模式下端点整体下线。
@RestController
@RequestMapping("/api/v1/upload")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class UploadController {

    private final DocumentService documentService;

    public UploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/init")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadInitResponseVo> initUpload(
            @Valid @RequestBody UploadInitDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.initUpload(request, idempotencyKey));
    }

    @PutMapping("/{uploadId}/parts/{partNumber}")
    public ResponseEntity<Void> uploadPart(
            @PathVariable String uploadId,
            @PathVariable int partNumber,
            @RequestBody byte[] content) {
        documentService.uploadPart(uploadId, partNumber, content);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uploadId}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> completeUpload(
            @PathVariable String uploadId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.completeUpload(uploadId, idempotencyKey));
    }
}
