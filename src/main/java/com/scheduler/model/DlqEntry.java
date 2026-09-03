package com.scheduler.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// This is DLQ Entity which is used for replaying failed tasks - it will have the task details and it's status like replayed or not
// and the timstamp at which it is replayed, so currently - it will replay the tasks with exact same details - like without editing any Detail
//  so if any task which got failed due to any input absence or any logic then it will fail again, 
// improvisation need to allow editing the payload or logical errors before replaying, so a valid success can happen

@Entity
@Table(name = "dlq_entries")
public class DlqEntry {

    @Id
    private String id;

    @Column(nullable = false)
    private String taskId;

    private String taskName;

    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private int retryCount;
    private int maxRetries;
    private String workerName;
    private LocalDateTime failedAt;
    private boolean replayed;
    private LocalDateTime replayedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = "dlq-" + UUID.randomUUID().toString().substring(0, 8);
        if (failedAt == null) failedAt = LocalDateTime.now();
    }


    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }

    public LocalDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(LocalDateTime failedAt) { this.failedAt = failedAt; }

    public boolean isReplayed() { return replayed; }
    public void setReplayed(boolean replayed) { this.replayed = replayed; }

    public LocalDateTime getReplayedAt() { return replayedAt; }
    public void setReplayedAt(LocalDateTime replayedAt) { this.replayedAt = replayedAt; }
}
