package com.ragkb.service.modules.admin.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.admin.service.AdminService;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 装配条件：审计查询依赖数据库（ragkb.db.enabled=true），scaffold 模式下端点整体下线
// （与 document/knowledge/analytics/conversation 模块的条件装配约定一致）。
@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasAuthority('audit:read')")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
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
