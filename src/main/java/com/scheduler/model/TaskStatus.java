package com.scheduler.model;

// status of task
public enum TaskStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED
}