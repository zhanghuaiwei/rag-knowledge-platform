package com.ragkb.service.modules.admin.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.admin.dto.WebhookDto;
import com.ragkb.service.modules.admin.dto.WebhookToggleDto;
import com.ragkb.service.modules.admin.service.AdminService;
import com.ragkb.service.modules.admin.vo.WebhookDeliveryVo;
import com.ragkb.service.modules.admin.vo.WebhookVo;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('webhook:manage')")
public class WebhookController {

    private final AdminService adminService;

    public WebhookController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("webhook-subscriptions")
    public ApiResponse<List<WebhookVo>> listWebhooks() {
        return ApiResponse.ok(adminService.listWebhooks());
    }

    @PostMapping("webhook-subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WebhookVo> createWebhook(
            @Valid @RequestBody WebhookDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(adminService.createWebhook(request, idempotencyKey));
    }

    @PatchMapping("webhook-subscriptions/{subscriptionId}")
    public ApiResponse<WebhookVo> toggleWebhook(
            @PathVariable long subscriptionId,
            @Valid @RequestBody WebhookToggleDto request) {
        return ApiResponse.ok(adminService.toggleWebhook(subscriptionId, request));
    }

    @DeleteMapping("webhook-subscriptions/{subscriptionId}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable long subscriptionId) {
        adminService.deleteWebhook(subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("webhook-deliveries")
    public ApiResponse<PageData<WebhookDeliveryVo>> listWebhookDeliveries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(adminService.listWebhookDeliveries(page, size, status));
    }
}
