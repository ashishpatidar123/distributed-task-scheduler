package com.scheduler.handler;

import com.scheduler.model.TaskType;

public interface TaskHandler {
    TaskType getType();
    String execute(String taskId, String payload) throws Exception;
}