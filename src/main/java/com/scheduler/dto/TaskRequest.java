package com.scheduler.dto;

import com.scheduler.model.TaskPriority;
import com.scheduler.model.TaskType;

import java.util.List;

// template for the task request

public class TaskRequest {

    private String name;
    private TaskType type;
    private String payload;
    private TaskPriority priority;
    private Integer maxRetries;
    private List<String> dependsOn;
    private long timeoutMs;
    private String idempotentKey;

    public TaskRequest() {}

    public TaskRequest(String name, TaskType type, String payload, TaskPriority priority) {
        this.name = name;
        this.type = type;
        this.payload = payload;
        this.priority = priority;
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
    public void setType(TaskType type){
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
    public Integer getMaxRetries() {
        return maxRetries;
    }
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }
    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
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