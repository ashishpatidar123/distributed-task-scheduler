package com.scheduler.service;

import com.scheduler.dto.*;
import com.scheduler.model.*;
import com.scheduler.repository.*;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;

    public TaskService(TaskRepository taskRepository, TaskDependencyRepository taskDependencyRepository){
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
    }
    private static final java.util.regex.Pattern NAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]+$");

    public Task createTask(TaskRequest request){
        
        if(request.getIdempotentKey() != null && !request.getIdempotentKey().isEmpty() ) {
            Optional<Task> existingTask = taskRepository.findByIdempotentKey(request.getIdempotentKey());
            if(existingTask.isPresent()) {
                return existingTask.get(); // Return the existing task instead of creating a new one
            }
        }
        
        if(request.getName() != null && !NAME_PATTERN.matcher(request.getName()).matches()){
            throw new IllegalArgumentException("Task name can only contain letters, digits, and underscores");
        }
        
        Task task = new Task();
        task.setName(request.getName());
        task.setType(request.getType());
        task.setPayload(request.getPayload());
        task.setPriority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM);
        task.setMaxRetries(request.getMaxRetries());
        task.setTimeoutMs(request.getTimeoutMs());
        task.setIdempotentKey(request.getIdempotentKey());
        task.setStatus(TaskStatus.PENDING);
        Task saved  = taskRepository.save(task);

        if(request.getDependsOn() != null && !request.getDependsOn().isEmpty()){
            for(String depId :  request.getDependsOn()){
                taskDependencyRepository.save(new TaskDependency(saved.getId(), depId));
            }
        }

        return saved;

    }

    @Transactional(readOnly =  true)
    @Cacheable(value = "tasks", key = "#id", unless = "#result == null")
    public Optional<Task> getTask(String id){
        return taskRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Task> getAllTasks(){
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    public Task cancelTask(String id){
        Task task = taskRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Task not found: " + id));
        
        if(task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.FAILED){
            throw new RuntimeException("Cannot cancel task in status: " + task.getStatus());
        }

        task.setStatus(TaskStatus.CANCELLED);
        return taskRepository.save(task);
    }

    @CacheEvict(value = "tasks", key = "#task.id")
    public Task updateTask(Task task){
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public long countByStatus(TaskStatus status){
        return taskRepository.countByStatus(status);
    }
    
    @Transactional(readOnly = true)
    public long countAll(){
        return taskRepository.count();
    }

    @Transactional(readOnly = true)
    public Page<Task> getFilteredTasks(Specification<Task> spec, Pageable pageable){
        return taskRepository.findAll(spec, pageable);
    }
}
