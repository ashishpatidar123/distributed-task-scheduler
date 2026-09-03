package com.scheduler.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

//  the task entity, descibing the details about the tasks
//  also contains the details of the workflow of which the task is part of, if any

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_task_status", columnList = "status"),
        @Index(name = "idx_task_status_priority", columnList = "status, priority"),
        @Index(name = "idx_task_workflow", columnList = "workflowId"),
        @Index(name = "idx_task_created", columnList = "createdAt"),
        @Index(name = "idx_task_idempotency", columnList = "idempotentKey", unique = true)
})
public class Task{

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    private int retryCount;
    private int maxRetries;

    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;
    private LocalDateTime startedAt;

    private String assignedWorker;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String result;

    private long executionTimeMs;

    private long timeoutMs = 60000;

    private String workflowId;

    @Column(unique = true)
    private String idempotentKey;

    //  these are the values which will be avaiable if not provided, so auto filling
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8); // Generate a short UUID
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if(status == null) {
            status = TaskStatus.PENDING;
        }
        if(priority == null) {
            priority = TaskPriority.MEDIUM;
        }
        if(retryCount == 0) {
            retryCount = 0;
        }
        if(maxRetries == 0) {
            maxRetries = 3;
        }
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public TaskType getType() {
        return type;
    }
    public void setType(TaskType type) {
        this.type = type;
    }
    public String getPayload() {
        return payload;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }
    public TaskPriority getPriority() {
        return priority;
    }
    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }
    public TaskStatus getStatus() {
        return status;
    }
    public void setStatus(TaskStatus status) {
        this.status = status;
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
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }
    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    public String getAssignedWorker() {
        return assignedWorker;
    }
    public void setAssignedWorker(String assignedWorker) {
        this.assignedWorker = assignedWorker;
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
    public String getWorkflowId(){
        return workflowId;
    }
    public void setWorkflowId(String workflowId){
        this.workflowId = workflowId;
    }
    public long getTimeoutMs(){
        return timeoutMs;
    }
    public void setTimeoutMs(long timeoutMs){
        this.timeoutMs = timeoutMs;
    }
    public String getIdempotentKey() {
        return idempotentKey;
    }
    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

}