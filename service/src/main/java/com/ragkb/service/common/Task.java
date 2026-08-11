package com.ragkb.service.common;

import java.time.Instant;

/**
 * 通用异步任务（对齐前端 Task 类型）。
 *
 * <p>用于克隆 / 删除 / 索引构建 / 同步 / 解析等异步操作：控制器返回 202，
 * 前端可轮询 {@code GET /api/v1/tasks/{taskId}} 获取进度。
 */
public record Task(
        String id,
        String type,
        String status,
        String title,
        int progress,
        String resourceType,
        String resourceId,
        Instant startedAt,
        Instant finishedAt,
        String message) {

    public static Task of(String id, String type, String status, String title, int progress) {
        return new Task(id, type, status, title, progress, null, null, Instant.now(), null, null);
    }
}
