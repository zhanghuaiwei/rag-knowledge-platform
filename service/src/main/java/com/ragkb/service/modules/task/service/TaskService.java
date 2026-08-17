package com.ragkb.service.modules.task.service;

import com.ragkb.service.common.model.Task;

import java.util.List;

/**
 * 异步任务查询、登记与取消用例。
 *
 * <p>任务由各业务用例通过 {@link #submit} 登记（如上传完成、摄取入队），
 * 前端轮询 {@code GET /api/v1/tasks/{id}} 获取终态；实现为进程内内存注册表
 * （见 {@code TaskServiceImpl}），生产建议替换为任务表/任务中心存储。
 */
public interface TaskService {

    /** 任务列表（按开始时间倒序）。 */
    List<Task> listTasks();

    /** 查询单个异步任务进度；不存在抛 {@code NOT_FOUND}。 */
    Task getTask(String taskId);

    /** 取消任务（仅 PENDING/RUNNING 可取消）。 */
    void cancelTask(String taskId);

    /**
     * 登记一个新任务并返回带生成 id 的任务（供业务用例在异步操作入队/完成时调用）。
     *
     * @param type        任务类型（UPLOAD / INGEST / REPARSE / DELETE / ...）
     * @param status      任务状态（QUEUED / RUNNING / SUCCEEDED / ...）
     * @param title       展示标题
     * @param progress    0-100
     * @param resourceType 关联资源类型（DOCUMENT / KB / ...），可为 null
     * @param resourceId  关联资源 id（前端轮询终态后用于回读资源），可为 null
     * @param message     附加信息（失败原因等），可为 null
     */
    Task submit(String type, String status, String title, int progress,
                String resourceType, String resourceId, String message);
}
