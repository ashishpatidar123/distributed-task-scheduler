package com.scheduler.service;

import com.scheduler.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    @Value("${scheduler.base-retry-delay-ms:1000}")
    private long baseDelayMs;

    public boolean shouldRetry(Task task) {
        return task.getRetryCount() < task.getMaxRetries();
    }

    public long calculateDelayMs(int retryCount) {
        // Exponential backoff: baseDelay * 2^retryCount
        long delay = baseDelayMs * (long) Math.pow(2, retryCount);
        // Add jitter (up to 25% of delay) to prevent thundering herd
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, delay / 4));
        long totalDelay = delay + jitter;
        log.info("Calculated retry delay: {}ms (base={}ms, attempt={}, jitter={}ms)",
                totalDelay, delay, retryCount + 1, jitter);
        return totalDelay;
    }

    public LocalDateTime getNextRetryTime(int retryCount) {
        long delayMs = calculateDelayMs(retryCount);
        return LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);
    }
}
