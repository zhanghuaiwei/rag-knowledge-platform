package com.ragkb.service.modules.task.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.task.service.TaskService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步任务内存注册表实现。
 *
 * <p>用 {@link ConcurrentHashMap} 保存任务（线程安全），id 自增生成（形如 {@code T1/T2/...}）；
 * 任务不可变（record），状态翻转（如取消）通过「同 id 覆盖新对象」实现。
 *
 * <p>⚠️ 边界：进程内内存，重启/多副本后任务丢失（与
 * {@code document-upload-data-flow.md §5.5} 中 rag-engine 的内存任务仓库同一性质），
 * 仅用于开发联调；生产应换为任务表 + worker 心跳，本接口不变。
 */
@Service
public class TaskServiceImpl implements TaskService {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    @Override
    public List<Task> listTasks() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(Task::startedAt).reversed())
                .toList();
    }

    @Override
    public Task getTask(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    @Override
    public void cancelTask(String taskId) {
        Task task = getTask(taskId);
        if ("SUCCEEDED".equals(task.status()) || "FAILED".equals(task.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "任务已结束，无法取消");
        }
        // record 不可变：以同 id 覆盖为 CANCELLED 的新对象。
        tasks.put(taskId, new Task(task.id(), task.type(), "CANCELLED", task.title(), task.progress(),
                task.resourceType(), task.resourceId(), task.startedAt(), Instant.now(), task.message()));
    }

    @Override
    public Task submit(String type, String status, String title, int progress,
                       String resourceType, String resourceId, String message) {
        String id = "T" + idSeq.getAndIncrement();
        Instant now = Instant.now();
        Task task = new Task(id, type, status, title, Math.max(0, Math.min(100, progress)),
                resourceType, resourceId, now,
                "SUCCEEDED".equals(status) || "FAILED".equals(status) ? now : null,
                message);
        tasks.put(id, task);
        return task;
    }
}
