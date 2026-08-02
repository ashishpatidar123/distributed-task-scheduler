package com.scheduler.model;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

// worker informations, so that we can get the status of the workers, if they're available or not
public class WorkerInfo {

    private final String id;
    private final String name;
    private volatile WorkerStatus status;
    private volatile LocalDateTime lastHeartbeat;
    private final AtomicInteger tasksCompleted = new AtomicInteger(0);
    private final AtomicInteger tasksFailed = new AtomicInteger(0);
    private volatile String currentTaskId;


    public WorkerInfo(String id, String name) {
        this.id = id;
        this.name = name;
        this.status = WorkerStatus.IDLE;
        this.lastHeartbeat = LocalDateTime.now();
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }

    public void incrementCompleted() {
        tasksCompleted.incrementAndGet();
    }
    public void incrementFailed() {
        tasksFailed.incrementAndGet();
    }

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }
    public String getCurrentTaskId() {
        return currentTaskId;
    }
    public void setStatus(WorkerStatus status) {
        this.status = status;
    }
    public WorkerStatus getStatus() {
        return status;
    }
    public LocalDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }
    public int getTasksCompleted() {
        return tasksCompleted.get();
    }
    public int getTasksFailed() {
        return tasksFailed.get();
    }


}