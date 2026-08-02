package com.scheduler.service;

import com.scheduler.dto.SchedulerStats;
import com.scheduler.dto.TaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastTaskUpdate(TaskResponse task) {
        try {
            messagingTemplate.convertAndSend("/topic/tasks", task);
        } catch (Exception e) {
            log.warn("Failed to broadcast task update: {}", e.getMessage());
        }
    }

    public void broadcastStats(SchedulerStats stats) {
        try {
            messagingTemplate.convertAndSend("/topic/stats", stats);
        } catch (Exception e) {
            log.warn("Failed to broadcast stats: {}", e.getMessage());
        }
    }

    public void broadcastWorkerUpdate(Map<String, Object> workerData) {
        try {
            messagingTemplate.convertAndSend("/topic/workers", workerData);
        } catch (Exception e) {
            log.warn("Failed to broadcast worker update: {}", e.getMessage());
        }
    }
}