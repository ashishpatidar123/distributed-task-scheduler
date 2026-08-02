package com.scheduler.model;

import jakarta.persistence.*;

// this entity is used for defining the dependent tasks, like tasks have some dependents and and also on which it depends

@Entity
@Table(name = "task_dependencies")
public class TaskDependency{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String taskId;

    @Column(nullable = false)
    private String dependsOnTaskId;

    public TaskDependency() {}

    public TaskDependency(String taskId, String dependsOnTaskId){
        this.taskId = taskId;
        this.dependsOnTaskId = dependsOnTaskId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    public void setDependsOnTaskId(String dependsOnTaskId) {
        this.dependsOnTaskId = dependsOnTaskId;
    }
}