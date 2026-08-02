package com.scheduler.dto;

import java.util.Map;

// defining the sheduler stats - this is for tracking the stats of the service

public class SchedulerStats {
    private long totalTasks;
    private long pendingTasks;
    private long queuedTasks;
    private long runningTasks;
    private long completedTasks;
    private long failedTasks;
    private long retryingTasks;
    private int activeWorkers;
    private int idleWorkers;
    private int offlineWorkers;
    private int queueSize;
    private long taskCompletedLastMinute;
    private double throughputPerSecond;
    private int hashRingNodeCount;
    private Map<String, Integer> hashRingDistribution;
    private Map<String, Integer> workerTaskDistribution;
    
    private int totalWorkers;
    private long recoveredTasks;
    private long dlqCount;
    private long rateLimitRejections;

    public SchedulerStats() {}


    public long getTotalTasks() {
        return totalTasks;
    }
    public void setTotalTasks(long totalTasks) {
        this.totalTasks = totalTasks;
    }
    public long getPendingTasks() {
        return pendingTasks;
    }
    public void setPendingTasks(long pendingTasks) {
        this.pendingTasks = pendingTasks;
    }
    public long getQueuedTasks() {
        return queuedTasks;
    }
    public void setQueuedTasks(long queuedTasks) {
        this.queuedTasks = queuedTasks;
    }
    public long getRunningTasks() {
        return runningTasks;
    }
    public void setRunningTasks(long runningTasks) {
        this.runningTasks = runningTasks;
    }

    public long getCompletedTasks() {
        return completedTasks;
    }
    public void setCompletedTasks(long completedTasks) {
        this.completedTasks = completedTasks;
    }

    public long getFailedTasks() {
        return failedTasks;
    }
    public void setFailedTasks(long failedTasks) {
        this.failedTasks = failedTasks;
    }
    public long getRetryingTasks() {
        return retryingTasks;
    }
    public void setRetryingTasks(long retryingTasks) {
        this.retryingTasks = retryingTasks;
    }
    public int getActiveWorkers() {
        return activeWorkers;
    }
    public void setActiveWorkers(int activeWorkers) {
        this.activeWorkers = activeWorkers;
    }
    public int getIdleWorkers() {
        return idleWorkers;
    }
    public void setIdleWorkers(int idleWorkers) {
        this.idleWorkers = idleWorkers;
    }
    public int getOfflineWorkers() {
        return offlineWorkers;
    }
    public void setOfflineWorkers(int offlineWorkers) {
        this.offlineWorkers = offlineWorkers;
    }
    public int getQueueSize() {
        return queueSize;
    }
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }
    public long getTaskCompletedLastMinute() {
        return taskCompletedLastMinute;
    }

    public void setTaskCompletedLastMinute(long taskCompletedLastMinute) {
        this.taskCompletedLastMinute = taskCompletedLastMinute;
    }

    public double getThroughputPerSecond() {
        return throughputPerSecond;
    }

    public void setThroughputPerSecond(double throughputPerSecond) {
        this.throughputPerSecond = throughputPerSecond;
    }

    public int getHashRingNodeCount() {
    return hashRingNodeCount;
}

    public void setHashRingNodeCount(int hashRingNodeCount) {
        this.hashRingNodeCount = hashRingNodeCount;
    }

    public Map<String, Integer> getHashRingDistribution() {
        return hashRingDistribution;
    }

    public void setHashRingDistribution(Map<String, Integer> hashRingDistribution) {
        this.hashRingDistribution = hashRingDistribution;
    }

    public Map<String, Integer> getWorkerTaskDistribution() {
        return workerTaskDistribution;
    }

    public void setWorkerTaskDistribution(Map<String, Integer> workerTaskDistribution) {
        this.workerTaskDistribution = workerTaskDistribution;
    }

    
    public int getTotalWorkers() {
        return totalWorkers;
    }

    public void setTotalWorkers(int totalWorkers) {
        this.totalWorkers = totalWorkers;
    }

    public long getRecoveredTasks() {
        return recoveredTasks;
    }

    public void setRecoveredTasks(long recoveredTasks) {
        this.recoveredTasks = recoveredTasks;
    }

    public long getDlqCount() {
        return dlqCount;
    }

    public void setDlqCount(long dlqCount) {
        this.dlqCount = dlqCount;
    }

    public long getRateLimitRejections() {
        return rateLimitRejections;
    }

    public void setRateLimitRejections(long rateLimitRejections) {
        this.rateLimitRejections = rateLimitRejections;
    }

    
}