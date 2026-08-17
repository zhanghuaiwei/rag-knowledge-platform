package com.ragkb.service.modules.task.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.task.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异步任务接口入口。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // ---- 异步任务 ----

    @GetMapping("")
    public ApiResponse<List<Task>> listTasks() {
        return ApiResponse.ok(taskService.listTasks());
    }

    @GetMapping("/{taskId}")
    public ApiResponse<Task> getTask(@PathVariable String taskId) {
        return ApiResponse.ok(taskService.getTask(taskId));
    }

    @PostMapping("/{taskId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> cancelTask(@PathVariable String taskId) {
        taskService.cancelTask(taskId);
        return ResponseEntity.accepted().build();
    }
}
