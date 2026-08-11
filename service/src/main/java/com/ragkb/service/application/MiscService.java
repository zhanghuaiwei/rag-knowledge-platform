package com.ragkb.service.application;

import com.ragkb.service.common.Task;

import java.util.List;

/**
 * 杂项用例：异步任务 / 通知（实现点由人工完成）。
 */
public interface MiscService {

    List<Task> listTasks();

    /** 查询单个异步任务进度。 */
    Task getTask(String taskId);

    /** 取消任务（仅 PENDING/RUNNING 可取消）。 */
    void cancelTask(String taskId);
}
