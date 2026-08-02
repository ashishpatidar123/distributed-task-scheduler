package com.scheduler.repository;

import com.scheduler.model.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    // finding the tasks on which the given task is dependent
    List<TaskDependency> findByTaskId(String taskIdValue);

    // find the  // finding the tasks which depeends on the given task , downstream dependents
    List<TaskDependency> findByDependsOnTaskId(String dependsOnTaskIdValue);

    // deleting the task
    void deleteByTaskId(String taskIdValue);
}