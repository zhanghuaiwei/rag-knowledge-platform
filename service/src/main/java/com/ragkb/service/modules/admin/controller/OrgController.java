package com.ragkb.service.modules.admin.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.admin.dto.OrgDto;
import com.ragkb.service.modules.admin.service.AdminService;
import com.ragkb.service.modules.admin.vo.OrgVo;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

// 装配条件：组织管理依赖数据库持久化（ragkb.db.enabled=true），scaffold 模式下端点整体下线。
@RestController
@RequestMapping("/api/v1/orgs")
@PreAuthorize("hasAuthority('tenant-member:manage')")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class OrgController {

    private final AdminService adminService;

    public OrgController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("")
    public ApiResponse<List<OrgVo>> listOrgs() {
        return ApiResponse.ok(adminService.listOrgs());
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrgVo> createOrg(
            @Valid @RequestBody OrgDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(adminService.createOrg(request, idempotencyKey));
    }

    @PatchMapping("/{orgId}")
    public ApiResponse<OrgVo> updateOrg(@PathVariable long orgId, @Valid @RequestBody OrgDto request) {
        return ApiResponse.ok(adminService.updateOrg(orgId, request));
    }

    @DeleteMapping("/{orgId}")
    public ResponseEntity<Void> deleteOrg(@PathVariable long orgId) {
        adminService.deleteOrg(orgId);
        return ResponseEntity.noContent().build();
    }
}
