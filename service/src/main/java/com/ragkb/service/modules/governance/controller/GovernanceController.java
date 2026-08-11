package com.ragkb.service.modules.governance.controller;

import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.governance.vo.DeletionReceiptVo;
import com.ragkb.service.modules.governance.vo.DeletionTaskVo;
import com.ragkb.service.modules.governance.vo.LegalHoldVo;
import com.ragkb.service.modules.governance.dto.LegalHoldDto;
import com.ragkb.service.modules.governance.vo.MetadataSchemaVo;
import com.ragkb.service.modules.governance.dto.MetadataSchemaDto;
import com.ragkb.service.modules.governance.vo.RetentionPolicyVo;
import com.ragkb.service.modules.governance.dto.RetentionPolicyDto;
import com.ragkb.service.modules.governance.dto.RetentionPolicyToggleDto;
import com.ragkb.service.modules.governance.dto.ReviewActionDto;
import com.ragkb.service.modules.governance.vo.ReviewItemVo;
import com.ragkb.service.modules.governance.service.GovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 治理中心接口入口：元数据 schema / 审核 / 保留与法律保全 / 删除与证明。
 * 业务实现见 {@link GovernanceService}。
 */
@RestController
public class GovernanceController {

    private final GovernanceService governanceService;

    public GovernanceController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    // ---- 元数据 schema ----

    @GetMapping("/api/v1/metadata-schemas")
    public ApiResponse<List<MetadataSchemaVo>> listMetadataSchemas() {
        return ApiResponse.ok(governanceService.listMetadataSchemas());
    }

    @PostMapping("/api/v1/metadata-schemas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MetadataSchemaVo> createMetadataSchema(
            @Valid @RequestBody MetadataSchemaDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createMetadataSchema(request, idempotencyKey));
    }

    @PostMapping("/api/v1/metadata-schemas/{schemaId}/publish")
    public ApiResponse<MetadataSchemaVo> publishMetadataSchema(
            @PathVariable long schemaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.publishMetadataSchema(schemaId, idempotencyKey));
    }

    // ---- 内容审核 ----

    @GetMapping("/api/v1/reviews")
    public ApiResponse<PageData<ReviewItemVo>> listReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(governanceService.listReviews(page, size));
    }

    @PostMapping("/api/v1/reviews/{reviewId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveReview(
            @PathVariable long reviewId,
            @Valid @RequestBody(required = false) ReviewActionDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        governanceService.approveReview(reviewId, request == null ? new ReviewActionDto(null) : request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/api/v1/reviews/{reviewId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectReview(
            @PathVariable long reviewId,
            @Valid @RequestBody ReviewActionDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        governanceService.rejectReview(reviewId, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/api/v1/documents/{documentId}/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdrawDocument(
            @PathVariable long documentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        governanceService.withdrawDocument(documentId, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ---- 保留策略 ----

    @GetMapping("/api/v1/retention-policies")
    public ApiResponse<List<RetentionPolicyVo>> listRetentionPolicies() {
        return ApiResponse.ok(governanceService.listRetentionPolicies());
    }

    @PostMapping("/api/v1/retention-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RetentionPolicyVo> createRetentionPolicy(
            @Valid @RequestBody RetentionPolicyDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createRetentionPolicy(request, idempotencyKey));
    }

    /** 启停保留策略（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PatchMapping("/api/v1/retention-policies/{policyId}")
    public ApiResponse<RetentionPolicyVo> toggleRetentionPolicy(
            @PathVariable long policyId,
            @Valid @RequestBody RetentionPolicyToggleDto request) {
        return ApiResponse.ok(governanceService.toggleRetentionPolicy(policyId, request));
    }

    // ---- 法律保全 ----

    @GetMapping("/api/v1/legal-holds")
    public ApiResponse<List<LegalHoldVo>> listLegalHolds() {
        return ApiResponse.ok(governanceService.listLegalHolds());
    }

    @PostMapping("/api/v1/legal-holds")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LegalHoldVo> createLegalHold(
            @Valid @RequestBody LegalHoldDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createLegalHold(request, idempotencyKey));
    }

    /** 解除法律保全（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PostMapping("/api/v1/legal-holds/{holdId}/release")
    public ApiResponse<LegalHoldVo> releaseLegalHold(@PathVariable long holdId) {
        return ApiResponse.ok(governanceService.releaseLegalHold(holdId));
    }

    // ---- 删除审批与删除证明 ----

    @GetMapping("/api/v1/deletion-tasks")
    public ApiResponse<List<DeletionTaskVo>> listDeletionTasks() {
        return ApiResponse.ok(governanceService.listDeletionTasks());
    }

    @GetMapping("/api/v1/deletion-tasks/{taskId}")
    public ApiResponse<DeletionTaskVo> getDeletionTask(@PathVariable long taskId) {
        return ApiResponse.ok(governanceService.listDeletionTasks().stream()
                .filter(task -> task.id() == taskId)
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.exception.ApiException(
                        com.ragkb.service.common.exception.ErrorCode.NOT_FOUND, "删除任务不存在")));
    }

    /** 审批删除（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PostMapping("/api/v1/deletion-tasks/{taskId}/approve")
    public ApiResponse<DeletionTaskVo> approveDeletion(@PathVariable long taskId) {
        return ApiResponse.ok(governanceService.approveDeletion(taskId));
    }

    @GetMapping("/api/v1/deletion-receipts")
    public ApiResponse<List<DeletionReceiptVo>> listDeletionReceipts() {
        return ApiResponse.ok(governanceService.listDeletionReceipts());
    }

    @GetMapping("/api/v1/deletion-receipts/{receiptId}")
    public ApiResponse<DeletionReceiptVo> getDeletionReceipt(@PathVariable String receiptId) {
        return ApiResponse.ok(governanceService.listDeletionReceipts().stream()
                .filter(receipt -> receipt.id().equals(receiptId))
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.exception.ApiException(
                        com.ragkb.service.common.exception.ErrorCode.NOT_FOUND, "删除证明不存在")));
    }
}
