package com.scheduler.service;

import com.scheduler.dto.TaskEvent;
import com.scheduler.dto.TaskResponse;
import com.scheduler.model.Task;
import com.scheduler.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.task-submissions}")
    private String taskSubmissionsTopic;

    @Value("${kafka.topics.task-events}")
    private String taskEventsTopic;

    @Value("${kafka.topics.task-dlq}")
    private String taskDlqTopic;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Task Submissions

    public void submitTask(Task task) {
        TaskResponse response = TaskResponse.from(task);
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(taskSubmissionsTopic, task.getId(), response);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish task {} to Kafka: {}", task.getId(), ex.getMessage());
            } else {
                log.info("Task {} published to topic '{}' [partition={}, offset={}]",
                        task.getId(), taskSubmissionsTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    // Task Events 

    public void publishEvent(TaskEvent event) {
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(taskEventsTopic, event.getTaskId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event for task {}: {}", event.getTaskId(), ex.getMessage());
            } else {
                log.debug("Event published: {} -> {} [partition={}, offset={}]",
                        event.getTaskId(), event.getStatus(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    // Dead Letter Queue 

    public void sendToDlq(Task task) {
        TaskEvent dlqEvent = new TaskEvent(task.getId(), task.getName(), TaskStatus.FAILED);
        dlqEvent.setErrorMessage(task.getErrorMessage());
        dlqEvent.setRetryCount(task.getRetryCount());
        dlqEvent.setMaxRetries(task.getMaxRetries());
        dlqEvent.setExecutionTimeMs(task.getExecutionTimeMs());

        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(taskDlqTopic, task.getId(), dlqEvent);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send task {} to DLQ: {}", task.getId(), ex.getMessage());
            } else {
                log.warn("Task {} sent to DLQ after {} retries. Error: {}",
                        task.getId(), task.getRetryCount(), task.getErrorMessage());
            }
        });
    }
}