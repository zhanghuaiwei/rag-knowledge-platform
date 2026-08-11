package com.ragkb.service.application.impl;

import com.ragkb.service.application.MiscService;
import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.common.Task;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 杂项桩实现（实现点由人工替换）。
 */
@Service
public class MiscServiceImpl implements MiscService {

    @Override
    public List<Task> listTasks() {
        return NotYetImplemented.stub("MiscService#listTasks");
    }

    @Override
    public Task getTask(String taskId) {
        return NotYetImplemented.stub("MiscService#getTask");
    }

    @Override
    public void cancelTask(String taskId) {
        NotYetImplemented.stub("MiscService#cancelTask");
    }
}
