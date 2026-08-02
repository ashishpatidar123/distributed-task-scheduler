package com.scheduler.repository;

import com.scheduler.model.Task;
import com.scheduler.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, String>, JpaSpecificationExecutor<Task> {

    // finding the task with a given status
    List<Task> findByStatus(TaskStatus status);

    // finding the taks with given status and scheduled before a given time
    List<Task> findByStatusAndScheduledAtBefore(TaskStatus status, LocalDateTime time);

    // fiding the tasks by status then ordering based on priority in asceneding order and also for the 
    // same priority, order in ascendeing based on the created timestamp
    List<Task> findByStatusOrderByPriorityAscCreatedAtAsc(TaskStatus status);

    // counting tasks by status
    long countByStatus(TaskStatus status);

    // finding tasks and ordering with the created time, in the descending order, so latest one will appear at top
    List<Task> findAllByOrderByCreatedAtDesc();

    // finding tasks with a worklfow id
    List<Task> findByWorkflowId(String workflowId);

    // finding the taks with given status and started before a given time
    List<Task> findByStatusAndStartedAtBefore(TaskStatus status, LocalDateTime time);
    
    Optional<Task> findByIdempotentKey(String idempotentKey);
}