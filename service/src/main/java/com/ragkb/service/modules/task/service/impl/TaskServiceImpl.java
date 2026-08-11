package com.ragkb.service.modules.task.service.impl;

import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.task.service.TaskService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 异步任务 TODO 实现。
 */
@Service
public class TaskServiceImpl implements TaskService {

    @Override
    public List<Task> listTasks() {
        return TodoSupport.notImplemented("TaskService#listTasks");
    }

    @Override
    public Task getTask(String taskId) {
        return TodoSupport.notImplemented("TaskService#getTask");
    }

    @Override
    public void cancelTask(String taskId) {
        TodoSupport.notImplemented("TaskService#cancelTask");
    }
}
