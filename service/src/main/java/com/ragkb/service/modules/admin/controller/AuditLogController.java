package com.ragkb.service.modules.admin.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.admin.service.AdminService;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasAuthority('audit:read')")
public class AuditLogController {

    private final AdminService adminService;

    public AuditLogController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("")
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
}
