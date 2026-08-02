package com.scheduler.dto;

import com.scheduler.model.*;
import java.time.LocalDateTime;
import java.util.List;

// template for the task reponse

public class TaskResponse {

    private String id;
    private String name;
    private TaskType type;
    private String payload;
    private TaskPriority priority;
    private TaskStatus status;
    private LocalDateTime createdAt; 
    private int retryCount;
    private LocalDateTime scheduledAt;
    private int maxRetries;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String assignedWorker;
    private String result;
    private long executionTimeMs;
    private List<String>dependsOn;
    private List<String>dependents;
    private long timeoutMs;
    
    public static TaskResponse from(Task task) {
        TaskResponse response = new TaskResponse();
        response.id = task.getId();
        response.name = task.getName();
        response.type = task.getType();
        response.payload = task.getPayload();
        response.priority = task.getPriority();
        response.status = task.getStatus();
        response.createdAt = task.getCreatedAt();
        response.retryCount = task.getRetryCount();
        response.scheduledAt = task.getScheduledAt();
        response.maxRetries = task.getMaxRetries();
        response.startedAt = task.getStartedAt();
        response.completedAt = task.getCompletedAt();
        response.errorMessage = task.getErrorMessage();
        response.assignedWorker = task.getAssignedWorker();
        response.result = task.getResult();
        response.executionTimeMs = task.getExecutionTimeMs();
        response.timeoutMs = task.getTimeoutMs();
        
        return response;
    }

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public TaskType getType() {
        return type;
    }
    public String getPayload() {
        return payload;
    }
    public TaskPriority getPriority() {
        return priority;
    }
    public TaskStatus getStatus() {
        return status;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public int getRetryCount() {
        return retryCount;
    }
    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }
    public int getMaxRetries() {
        return maxRetries;
    }
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public String getAssignedWorker() {
        return assignedWorker;
    }
    public String getResult() {
        return result;
    }
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public List<String> getDependents() {
        return dependents;
    }

    public void setDependents(List<String> dependents) {
        this.dependents = dependents;
    }
    
}
