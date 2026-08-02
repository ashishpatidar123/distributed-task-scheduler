package com.scheduler.service;

import com.scheduler.dto.TaskEvent;
import com.scheduler.dto.TaskResponse;
import com.scheduler.model.*;
import com.scheduler.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TaskEventConsumerService {

    private static final Logger log = LoggerFactory.getLogger(TaskEventConsumerService.class);

    private final NotificationService notificationService;
    private final TaskRepository taskRepository;
    private final DlqRepository dlqRepository;

    public TaskEventConsumerService(NotificationService notificationService,
                                     TaskRepository taskRepository, DlqRepository dlqRepository) {
        this.notificationService = notificationService;
        this.taskRepository = taskRepository;
        this.dlqRepository = dlqRepository;
    }

    @KafkaListener(
            topics = "${kafka.topics.task-events}",
            containerFactory = "taskEventListenerFactory"
    )
    public void onTaskEvent(TaskEvent event) {
        log.info("Received task event from Kafka: task={}, status={}, worker={}",
                event.getTaskId(), event.getStatus(), event.getWorkerName());

        // Load the full task from DB and broadcast via WebSocket
        Optional<Task> optTask = taskRepository.findById(event.getTaskId());
        if (optTask.isPresent()) {
            notificationService.broadcastTaskUpdate(TaskResponse.from(optTask.get()));
        } else {
            log.warn("Task {} not found in DB for event broadcast", event.getTaskId());
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.task-dlq}",
            groupId = "task-scheduler-dlq-group",
            containerFactory = "taskEventListenerFactory"
    )
    public void onDlqEvent(TaskEvent event) {
        log.error("DLQ: Task {} permanently failed after {} retries. Error: {}",
                event.getTaskId(), event.getRetryCount(), event.getErrorMessage());

        // persist dlq entry for dashboard and replay

        try{
            Optional<Task> optTask = taskRepository.findById(event.getTaskId());
            DlqEntry entry = new DlqEntry();
            entry.setTaskId(event.getTaskId());
            entry.setTaskName(event.getTaskName());
            entry.setErrorMessage(event.getErrorMessage());
            entry.setRetryCount(event.getRetryCount());
            entry.setMaxRetries(event.getMaxRetries());
            entry.setWorkerName(event.getWorkerName());

            if(optTask.isPresent()){
                Task task = optTask.get();
                entry.setTaskType(task.getType());
                entry.setPayload(task.getPayload());
                entry.setPriority(task.getPriority());
            }

            dlqRepository.save(entry);
            log.info("DLQ entry persisted for task {}", event.getTaskId());

        }
        catch(Exception e){
            log.error("Failed to persist DLQ entry for task {}: {}", event.getTaskId(), e.getMessage());
        }
    }
}
