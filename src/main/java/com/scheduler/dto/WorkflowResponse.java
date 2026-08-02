package com.scheduler.dto;

import com.scheduler.model.Workflow;
import com.scheduler.model.WorkflowStatus;

import java.time.LocalDateTime;
import java.util.List;

// workflow response

public class WorkflowResponse {

    private String id;
    private String name;
    private String description;
    private WorkflowStatus status;

    private int totalTasks;
    private int completedTasks;
    private int failedTasks;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<TaskResponse> tasks;

    public static WorkflowResponse from(Workflow workflow) {
        if (workflow == null) {
            return null;
        }

        WorkflowResponse r = new WorkflowResponse();
        r.id = workflow.getId();
        r.name = workflow.getName();
        r.description = workflow.getDescription();
        r.status = workflow.getStatus();
        r.totalTasks = workflow.getTotalTasks();
        r.completedTasks = workflow.getCompletedTasks();
        r.failedTasks = workflow.getFailedTasks();
        r.createdAt = workflow.getCreatedAt();
        r.completedAt = workflow.getCompletedAt();

        return r;
    }

    public static WorkflowResponse from(Workflow workflow, List<TaskResponse> tasks){
        WorkflowResponse r = from(workflow);
        r.tasks = tasks;
        return r;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }


    public String getDescription() {
        return description;
    }


    public WorkflowStatus getStatus() {
        return status;
    }


    public int getTotalTasks() {
        return totalTasks;
    }


    public int getCompletedTasks() {
        return completedTasks;
    }


    public int getFailedTasks() {
        return failedTasks;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }


    public List<TaskResponse> getTasks() {
        return tasks;
    }

}