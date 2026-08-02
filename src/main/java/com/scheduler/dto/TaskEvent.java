package com.scheduler.dto;

import com.scheduler.model.TaskStatus;

import java.time.LocalDateTime;

// defining task as an event for the kafka submission

public class TaskEvent{

    private String taskId;
    private String taskName;
    private TaskStatus status;
    private String workerName;
    private String errorMessage;
    private String result;
    private long executionTimeMs;
    private int retryCount;
    private int maxRetries;
    private LocalDateTime timestamp;

    public TaskEvent(){}

    public TaskEvent(String taskId, String taskName, TaskStatus status) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public static TaskEvent of(String taskId, String taskName, TaskStatus status, String workerName) {
        TaskEvent event = new TaskEvent(taskId, taskName, status);
        event.setWorkerName(workerName);
        return event;
    }

    public String getTaskId() {
        return taskId;
    }
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    public String getTaskName() {
        return taskName;
    }
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public TaskStatus getStatus() {
        return status;
    }
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    public String getWorkerName() {
        return workerName;
    }
    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
    public int getRetryCount() {
        return retryCount;
    }
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    public int getMaxRetries() {
        return maxRetries;
    }
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString(){
        return "TaskEvent{taskId='" + taskId + "', status=" + status +
                ", workerName='" + workerName + "', timestamp=" + timestamp + "}";
    }
}