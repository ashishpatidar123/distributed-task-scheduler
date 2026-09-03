package com.scheduler.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// task attempt template - used for execution

@Entity
@Table(name = "task_attempts")
public class TaskAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String taskId;

    private int attemptNumber;
    private String workerName;

    @Enumerated(EnumType.STRING)
    private TaskStatus resultStatus;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private long durationMs;

    @Column(columnDefinition="TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String result;

    

    // Default constructor required by JPA
    public TaskAttempt() {
    }

    // starting a new attempt for a task
    public static TaskAttempt start(String taskId, int attemptNumber, String workerName) {
        TaskAttempt attempt = new TaskAttempt();
        attempt.taskId = taskId;
        attempt.attemptNumber = attemptNumber;
        attempt.workerName = workerName;
        attempt.startedAt = LocalDateTime.now();
        return attempt;
    }

    // marking the completion of a task

    public void complete(String result, long durationMs){
        this.resultStatus = TaskStatus.COMPLETED;
        this.result = result;
        this.durationMs = durationMs;
        this.endedAt = LocalDateTime.now();
    }

    // marking the failure of a task
    public void fail(String errorMessage, long durationMs){
        this.resultStatus = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.endedAt = LocalDateTime.now();
    }

    

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public TaskStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(TaskStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
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

    // --- toString() ---

    
}