package com.ragkb.service.modules.admin.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.admin.vo.NotificationItemVo;
import com.ragkb.service.modules.admin.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户通知接口入口。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final AdminService adminService;

    public NotificationController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("")
    public ApiResponse<List<NotificationItemVo>> listNotifications() {
        return ApiResponse.ok(adminService.listNotifications());
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable long notificationId) {
        adminService.markNotificationRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllNotificationsRead() {
        adminService.markAllNotificationsRead();
        return ResponseEntity.noContent().build();
    }
}
