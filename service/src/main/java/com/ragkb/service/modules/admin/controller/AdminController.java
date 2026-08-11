package com.ragkb.service.modules.admin.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import com.ragkb.service.modules.admin.vo.OrgVo;
import com.ragkb.service.modules.admin.dto.OrgDto;
import com.ragkb.service.modules.admin.dto.UserOrgDto;
import com.ragkb.service.modules.admin.dto.WebhookDto;
import com.ragkb.service.modules.admin.dto.WebhookToggleDto;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import com.ragkb.service.modules.admin.vo.OrgVo;
import com.ragkb.service.modules.admin.vo.UserVo;
import com.ragkb.service.modules.admin.vo.WebhookDeliveryVo;
import com.ragkb.service.modules.admin.vo.WebhookVo;
import com.ragkb.service.modules.admin.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 管理中心接口入口：成员 / 组织 / 审计 / WebhookVo。业务实现见 {@link AdminService}。
 *
 * <p>用户管理（users）端点 OpenAPI 草案未定义，为产品契约所需新增。
 */
@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ---- 成员 ----

    @GetMapping("/api/v1/users")
    public ApiResponse<PageData<UserVo>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listUsers(page, size));
    }

    @PostMapping("/api/v1/users/{userId}/disable")
    public ApiResponse<UserVo> disableUser(@PathVariable long userId) {
        return ApiResponse.ok(adminService.disableUser(userId));
    }

    @PostMapping("/api/v1/users/{userId}/enable")
    public ApiResponse<UserVo> enableUser(@PathVariable long userId) {
        return ApiResponse.ok(adminService.enableUser(userId));
    }

    @PatchMapping("/api/v1/users/{userId}/org")
    public ApiResponse<UserVo> updateUserOrg(@PathVariable long userId, @RequestBody UserOrgDto request) {
        return ApiResponse.ok(adminService.updateUserOrg(userId, request.orgId()));
    }

    // ---- 组织 ----

    @GetMapping("/api/v1/orgs")
    public ApiResponse<List<OrgVo>> listOrgs() {
        return ApiResponse.ok(adminService.listOrgs());
    }

    @PostMapping("/api/v1/orgs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrgVo> createOrg(
            @Valid @RequestBody OrgDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(adminService.createOrg(request, idempotencyKey));
    }

    @PatchMapping("/api/v1/orgs/{orgId}")
    public ApiResponse<OrgVo> updateOrg(@PathVariable long orgId, @Valid @RequestBody OrgDto request) {
        return ApiResponse.ok(adminService.updateOrg(orgId, request));
    }

    @DeleteMapping("/api/v1/orgs/{orgId}")
    public ResponseEntity<Void> deleteOrg(@PathVariable long orgId) {
        adminService.deleteOrg(orgId);
        return ResponseEntity.noContent().build();
    }

    // ---- 审计 ----

    @GetMapping("/api/v1/audit-logs")
    public ApiResponse<PageData<AuditLogEntryVo>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String result) {
        return ApiResponse.ok(adminService.listAuditLogs(
                page, size, action, resourceType, actorId, dateFrom, dateTo, result));
    }

    // ---- WebhookVo ----

    @GetMapping("/api/v1/webhook-subscriptions")
    public ApiResponse<List<WebhookVo>> listWebhooks() {
        return ApiResponse.ok(adminService.listWebhooks());
    }

    @PostMapping("/api/v1/webhook-subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WebhookVo> createWebhook(
            @Valid @RequestBody WebhookDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(adminService.createWebhook(request, idempotencyKey));
    }

    @PatchMapping("/api/v1/webhook-subscriptions/{subscriptionId}")
    public ApiResponse<WebhookVo> toggleWebhook(
            @PathVariable long subscriptionId,
            @Valid @RequestBody WebhookToggleDto request) {
        return ApiResponse.ok(adminService.toggleWebhook(subscriptionId, request));
    }

    @DeleteMapping("/api/v1/webhook-subscriptions/{subscriptionId}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable long subscriptionId) {
        adminService.deleteWebhook(subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/webhook-deliveries")
    public ApiResponse<PageData<WebhookDeliveryVo>> listWebhookDeliveries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(adminService.listWebhookDeliveries(page, size, status));
    }
}
