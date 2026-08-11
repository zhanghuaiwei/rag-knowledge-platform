package com.ragkb.service.interfaces;

import com.ragkb.service.application.AdminService;
import com.ragkb.service.application.MiscService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.AdminDtos.NotificationItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 杂项接口入口：通知 / 异步任务（OpenAPI 草案未定义，为产品契约所需新增）。
 */
@RestController
public class MiscController {

    private final AdminService adminService;
    private final MiscService miscService;

    public MiscController(AdminService adminService, MiscService miscService) {
        this.adminService = adminService;
        this.miscService = miscService;
    }

    // ---- 通知 ----

    @GetMapping("/api/v1/notifications")
    public ApiResponse<List<NotificationItem>> listNotifications() {
        return ApiResponse.ok(adminService.listNotifications());
    }

    @PostMapping("/api/v1/notifications/{notificationId}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable long notificationId) {
        adminService.markNotificationRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/notifications/read-all")
    public ResponseEntity<Void> markAllNotificationsRead() {
        adminService.markAllNotificationsRead();
        return ResponseEntity.noContent().build();
    }

    // ---- 异步任务 ----

    @GetMapping("/api/v1/tasks")
    public ApiResponse<List<Task>> listTasks() {
        return ApiResponse.ok(miscService.listTasks());
    }

    @GetMapping("/api/v1/tasks/{taskId}")
    public ApiResponse<Task> getTask(@PathVariable String taskId) {
        return ApiResponse.ok(miscService.getTask(taskId));
    }

    @PostMapping("/api/v1/tasks/{taskId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> cancelTask(@PathVariable String taskId) {
        miscService.cancelTask(taskId);
        return ResponseEntity.accepted().build();
    }
}
