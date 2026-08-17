package com.ragkb.service.modules.governance.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.governance.dto.LegalHoldDto;
import com.ragkb.service.modules.governance.dto.RetentionPolicyDto;
import com.ragkb.service.modules.governance.dto.RetentionPolicyToggleDto;
import com.ragkb.service.modules.governance.vo.LegalHoldVo;
import com.ragkb.service.modules.governance.vo.RetentionPolicyVo;
import com.ragkb.service.modules.governance.service.GovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 治理中心 - 保留策略与法律保全接口。业务实现见 {@link GovernanceService}。
 */
@RestController
@RequestMapping("/api/v1")
public class RetentionAndLegalHoldController {

    private final GovernanceService governanceService;

    public RetentionAndLegalHoldController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    // ---- 保留策略 ----

    @GetMapping("/retention-policies")
    public ApiResponse<List<RetentionPolicyVo>> listRetentionPolicies() {
        return ApiResponse.ok(governanceService.listRetentionPolicies());
    }

    @PostMapping("/retention-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RetentionPolicyVo> createRetentionPolicy(
            @Valid @RequestBody RetentionPolicyDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createRetentionPolicy(request, idempotencyKey));
    }

    /** 启停保留策略（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PatchMapping("/retention-policies/{policyId}")
    public ApiResponse<RetentionPolicyVo> toggleRetentionPolicy(
            @PathVariable long policyId,
            @Valid @RequestBody RetentionPolicyToggleDto request) {
        return ApiResponse.ok(governanceService.toggleRetentionPolicy(policyId, request));
    }

    // ---- 法律保全 ----

    @GetMapping("/legal-holds")
    public ApiResponse<List<LegalHoldVo>> listLegalHolds() {
        return ApiResponse.ok(governanceService.listLegalHolds());
    }

    @PostMapping("/legal-holds")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LegalHoldVo> createLegalHold(
            @Valid @RequestBody LegalHoldDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createLegalHold(request, idempotencyKey));
    }

    /** 解除法律保全（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PostMapping("/legal-holds/{holdId}/release")
    public ApiResponse<LegalHoldVo> releaseLegalHold(@PathVariable long holdId) {
        return ApiResponse.ok(governanceService.releaseLegalHold(holdId));
    }
}
