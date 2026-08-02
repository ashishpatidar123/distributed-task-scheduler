package com.scheduler.dto;

import java.util.List;

// a workflow request template

public class WorkflowRequest {

    private String name;
    private String description;
    private List<WorkflowTaskNode> tasks;

    public WorkflowRequest(){}

    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name = name;
    }
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<WorkflowTaskNode> getTasks(){
        return tasks;
    }
    public void setTasks(List<WorkflowTaskNode> tasks){
        this.tasks = tasks;
    }

    // this is for a task inside a workflow
    public static class WorkflowTaskNode{
        private String tempId;
        private String name;
        private String type;
        private String priority;
        private String payload;
        private Integer maxRetries;
        private List<String> dependsOn;

        public String getTempId() {
            return tempId;
        }

        public void setTempId(String tempId) {
            this.tempId = tempId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
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

    }
    
    
}
