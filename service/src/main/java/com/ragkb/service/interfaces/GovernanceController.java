package com.ragkb.service.interfaces;

import com.ragkb.service.application.GovernanceService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.common.PageData;
import com.ragkb.service.interfaces.dto.GovernanceDtos.DeletionReceipt;
import com.ragkb.service.interfaces.dto.GovernanceDtos.DeletionTask;
import com.ragkb.service.interfaces.dto.GovernanceDtos.LegalHold;
import com.ragkb.service.interfaces.dto.GovernanceDtos.LegalHoldInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.MetadataSchema;
import com.ragkb.service.interfaces.dto.GovernanceDtos.MetadataSchemaInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicy;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicyInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicyToggleRequest;
import com.ragkb.service.interfaces.dto.GovernanceDtos.ReviewActionRequest;
import com.ragkb.service.interfaces.dto.GovernanceDtos.ReviewItem;
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
    public ApiResponse<List<MetadataSchema>> listMetadataSchemas() {
        return ApiResponse.ok(governanceService.listMetadataSchemas());
    }

    @PostMapping("/api/v1/metadata-schemas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MetadataSchema> createMetadataSchema(
            @Valid @RequestBody MetadataSchemaInput request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createMetadataSchema(request, idempotencyKey));
    }

    @PostMapping("/api/v1/metadata-schemas/{schemaId}/publish")
    public ApiResponse<MetadataSchema> publishMetadataSchema(
            @PathVariable long schemaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.publishMetadataSchema(schemaId, idempotencyKey));
    }

    // ---- 内容审核 ----

    @GetMapping("/api/v1/reviews")
    public ApiResponse<PageData<ReviewItem>> listReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(governanceService.listReviews(page, size));
    }

    @PostMapping("/api/v1/reviews/{reviewId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveReview(
            @PathVariable long reviewId,
            @Valid @RequestBody(required = false) ReviewActionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        governanceService.approveReview(reviewId, request == null ? new ReviewActionRequest(null) : request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/api/v1/reviews/{reviewId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectReview(
            @PathVariable long reviewId,
            @Valid @RequestBody ReviewActionRequest request,
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
    public ApiResponse<List<RetentionPolicy>> listRetentionPolicies() {
        return ApiResponse.ok(governanceService.listRetentionPolicies());
    }

    @PostMapping("/api/v1/retention-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RetentionPolicy> createRetentionPolicy(
            @Valid @RequestBody RetentionPolicyInput request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createRetentionPolicy(request, idempotencyKey));
    }

    /** 启停保留策略（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PatchMapping("/api/v1/retention-policies/{policyId}")
    public ApiResponse<RetentionPolicy> toggleRetentionPolicy(
            @PathVariable long policyId,
            @Valid @RequestBody RetentionPolicyToggleRequest request) {
        return ApiResponse.ok(governanceService.toggleRetentionPolicy(policyId, request));
    }

    // ---- 法律保全 ----

    @GetMapping("/api/v1/legal-holds")
    public ApiResponse<List<LegalHold>> listLegalHolds() {
        return ApiResponse.ok(governanceService.listLegalHolds());
    }

    @PostMapping("/api/v1/legal-holds")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LegalHold> createLegalHold(
            @Valid @RequestBody LegalHoldInput request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createLegalHold(request, idempotencyKey));
    }

    /** 解除法律保全（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PostMapping("/api/v1/legal-holds/{holdId}/release")
    public ApiResponse<LegalHold> releaseLegalHold(@PathVariable long holdId) {
        return ApiResponse.ok(governanceService.releaseLegalHold(holdId));
    }

    // ---- 删除审批与删除证明 ----

    @GetMapping("/api/v1/deletion-tasks")
    public ApiResponse<List<DeletionTask>> listDeletionTasks() {
        return ApiResponse.ok(governanceService.listDeletionTasks());
    }

    @GetMapping("/api/v1/deletion-tasks/{taskId}")
    public ApiResponse<DeletionTask> getDeletionTask(@PathVariable long taskId) {
        return ApiResponse.ok(governanceService.listDeletionTasks().stream()
                .filter(task -> task.id() == taskId)
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.ApiException(
                        com.ragkb.service.common.ErrorCode.NOT_FOUND, "删除任务不存在")));
    }

    /** 审批删除（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PostMapping("/api/v1/deletion-tasks/{taskId}/approve")
    public ApiResponse<DeletionTask> approveDeletion(@PathVariable long taskId) {
        return ApiResponse.ok(governanceService.approveDeletion(taskId));
    }

    @GetMapping("/api/v1/deletion-receipts")
    public ApiResponse<List<DeletionReceipt>> listDeletionReceipts() {
        return ApiResponse.ok(governanceService.listDeletionReceipts());
    }

    @GetMapping("/api/v1/deletion-receipts/{receiptId}")
    public ApiResponse<DeletionReceipt> getDeletionReceipt(@PathVariable String receiptId) {
        return ApiResponse.ok(governanceService.listDeletionReceipts().stream()
                .filter(receipt -> receipt.id().equals(receiptId))
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.ApiException(
                        com.ragkb.service.common.ErrorCode.NOT_FOUND, "删除证明不存在")));
    }
}
