package com.scheduler.model;

import java.time.LocalDateTime;

// this is the queue item, used to process tasks based on the order, mainly on priority

public class TaskQueueItem implements Comparable<TaskQueueItem> {
    
    private final String taskId;
    private final int priority;
    private final LocalDateTime createdAt;
    private final long enqueuedAtMs;

    public TaskQueueItem(String taskId, TaskPriority priority, LocalDateTime createdAt) {
        this.taskId = taskId;
        this.priority = priority.getValue();
        this.createdAt = createdAt;
        this.enqueuedAtMs = System.currentTimeMillis();
    }

    //  this is a fucntion used for comparing the effective order of the tasks
    //  like if any low priority tasks is queued for a long time then we will add a age boost to it, like we want
    //  it to process before any higher priority tasks

    @Override
    public int compareTo(TaskQueueItem other) {

        // checking how long a tasks is waiting in the queue and also other task
        long ageBoostThis = (System.currentTimeMillis() - this.enqueuedAtMs) / 10_000; 
        long ageBoostOther = (System.currentTimeMillis() - other.enqueuedAtMs) / 10_000; 
        // we're now calcuting the effective time for botht the tasks, so we're lowring the priortity
        // of older tasks, moving them closer to the front to process early
        long effectiveThis = this.priority - ageBoostThis;
        long effectiveOther = other.priority - ageBoostOther;

        // if the effective priorities are different then lower effective priorty tasks will go first
        int cmp = Long.compare(effectiveThis, effectiveOther);
        if (cmp != 0) {
            return cmp;
        }
        // this is for the same effective priority, doing FIFO
        return this.createdAt.compareTo(other.createdAt);
    }

    public String getTaskId() {
        return taskId;
    }
    public int getPriority() {
        return priority;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public long getEnqueuedAtMs() {
        return enqueuedAtMs;
    }
    @Override
    public String toString() {
        return "TaskQueueItem{taskId='" + taskId + "', priority=" + priority + ", createdAt=" + createdAt + ", enqueuedAtMs=" + enqueuedAtMs + "}";
    }


}