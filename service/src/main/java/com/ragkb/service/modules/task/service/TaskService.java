package com.ragkb.service.modules.task.service;

import com.ragkb.service.common.model.Task;

import java.util.List;

/**
 * 异步任务查询与取消用例。
 */
public interface TaskService {

    List<Task> listTasks();

    /** 查询单个异步任务进度。 */
    Task getTask(String taskId);

    /** 取消任务（仅 PENDING/RUNNING 可取消）。 */
    void cancelTask(String taskId);
}
