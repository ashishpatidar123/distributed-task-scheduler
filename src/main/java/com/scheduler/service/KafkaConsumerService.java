package com.scheduler.service;

import com.scheduler.dto.TaskResponse;
import com.scheduler.model.Task;
import com.scheduler.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final TaskRepository taskRepository;
    private final SchedulerService schedulerService;

    public KafkaConsumerService(TaskRepository taskRepository, SchedulerService schedulerService) {
        this.taskRepository = taskRepository;
        this.schedulerService = schedulerService;
    }

    @KafkaListener(
            topics = "${kafka.topics.task-submissions}",
            containerFactory = "taskSubmissionListenerFactory"
    )
    public void onTaskSubmission(TaskResponse taskResponse) {
        log.info("Received task from Kafka: id={}, name='{}', priority={}",
                taskResponse.getId(), taskResponse.getName(), taskResponse.getPriority());

        Optional<Task> optTask = taskRepository.findById(taskResponse.getId());

        if (optTask.isEmpty()) {
            log.warn("Task {} not found in DB, skipping Kafka message", taskResponse.getId());
            return;
        }
        Task task = optTask.get();
        schedulerService.enqueue(task);

        log.info("Task {} enqueud from Kafka consumer", task.getId());
    }
}