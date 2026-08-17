package com.ragkb.service.modules.governance.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.governance.dto.ReviewActionDto;
import com.ragkb.service.modules.governance.vo.ReviewItemVo;
import com.ragkb.service.modules.governance.service.GovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 治理中心 - 内容审核接口（含审核 / 文档撤回）。业务实现见 {@link GovernanceService}。
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final GovernanceService governanceService;

    public ReviewController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping("/reviews")
    public ApiResponse<PageData<ReviewItemVo>> listReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(governanceService.listReviews(page, size));
    }

    @PostMapping("/reviews/{reviewId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveReview(
            @PathVariable long reviewId,
            @Valid @RequestBody(required = false) ReviewActionDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        governanceService.approveReview(reviewId, request == null ? new ReviewActionDto(null) : request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/reviews/{reviewId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectReview(
            @PathVariable long reviewId,
            @Valid @RequestBody ReviewActionDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        governanceService.rejectReview(reviewId, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/documents/{documentId}/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdrawDocument(
            @PathVariable long documentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        governanceService.withdrawDocument(documentId, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
