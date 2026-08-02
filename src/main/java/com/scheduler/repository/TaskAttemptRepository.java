package com.scheduler.repository;

import com.scheduler.model.TaskAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAttemptRepository extends JpaRepository<TaskAttempt, Long>{

    // finding the tasks by counting the attemps in the asceneding order
    // so with the least attempts will appear at the top of the list
    List<TaskAttempt> findByTaskIdOrderByAttemptNumberAsc(String taskId);
    
}
