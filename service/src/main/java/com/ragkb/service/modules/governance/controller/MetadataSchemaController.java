package com.ragkb.service.modules.governance.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.governance.dto.MetadataSchemaDto;
import com.ragkb.service.modules.governance.vo.MetadataSchemaVo;
import com.ragkb.service.modules.governance.service.GovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 治理中心 - 元数据 schema 接口。业务实现见 {@link GovernanceService}。
 */
@RestController
@RequestMapping("/api/v1/metadata-schemas")
public class MetadataSchemaController {

    private final GovernanceService governanceService;

    public MetadataSchemaController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping("")
    public ApiResponse<List<MetadataSchemaVo>> listMetadataSchemas() {
        return ApiResponse.ok(governanceService.listMetadataSchemas());
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MetadataSchemaVo> createMetadataSchema(
            @Valid @RequestBody MetadataSchemaDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.createMetadataSchema(request, idempotencyKey));
    }

    @PostMapping("/{schemaId}/publish")
    public ApiResponse<MetadataSchemaVo> publishMetadataSchema(
            @PathVariable long schemaId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(governanceService.publishMetadataSchema(schemaId, idempotencyKey));
    }
}
