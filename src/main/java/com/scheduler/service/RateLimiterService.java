package com.scheduler.service;

import com.scheduler.model.TaskType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket rate limiter - global + per task type.
 *
 * Each bucket has:
 *   - capacity: max burst tokens
 *   - refillRate: tokens added per second
 *   - tokens: current available tokens
 *   - lastRefill: timestamp of last refill
 *
 * A submission consumes 1 token from the global bucket AND the per-type bucket.
 * If either bucket is empty -> request rejected (429).
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    @Value("${scheduler.rate-limit.global-capacity:10}")
    private int globalCapacity;

    @Value("${scheduler.rate-limit.global-refill-per-sec:2}")
    private double globalRefillPerSec;

    @Value("${scheduler.rate-limit.per-type-capacity:5}")
    private int perTypeCapacity;

    @Value("${scheduler.rate-limit.per-type-refill-per-sec:1}")
    private double perTypeRefillPerSec;

    @Value("${scheduler.rate-limit.enabled:true}")
    private boolean enabled;

    private TokenBucket globalBucket;
    private final ConcurrentHashMap<TaskType, TokenBucket> typeBuckets = new ConcurrentHashMap<>();
    private final AtomicLong totalRejections = new AtomicLong(0);

    @PostConstruct
    public void init() {
        globalBucket = new TokenBucket(globalCapacity, globalRefillPerSec);
        for (TaskType type : TaskType.values()) {
            typeBuckets.put(type, new TokenBucket(perTypeCapacity, perTypeRefillPerSec));
        }
        log.info("Rate limiter initialized: global={}/{}/s, perType={}/{}/s, enabled={}",
                globalCapacity, globalRefillPerSec, perTypeCapacity, perTypeRefillPerSec, enabled);
    }

    /**
     * Try to consume a token for a task submission.
     * @return true if allowed, false if rate limited
     */
    public boolean tryAcquire(TaskType type) {
        if (!enabled) return true;

        // Must pass BOTH global and per-type check
        boolean globalOk = globalBucket.tryConsume();
        if (!globalOk) {
            totalRejections.incrementAndGet();
            log.warn("[RateLimiter] Global rate limit exceeded for {}", type);
            return false;
        }

        TokenBucket typeBucket = typeBuckets.get(type);
        if (typeBucket != null && !typeBucket.tryConsume()) {
            totalRejections.incrementAndGet();
            log.warn("[RateLimiter] Per-type rate limit exceeded for {}", type);
            return false;
        }

        return true;
    }

    public long getTotalRejections() {
        return totalRejections.get();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("totalRejections", totalRejections.get());
        status.put("global", globalBucket.getStatus());

        Map<String, Object> types = new LinkedHashMap<>();
        for (TaskType type : TaskType.values()) {
            TokenBucket b = typeBuckets.get(type);
            if (b != null) types.put(type.name(), b.getStatus());
        }
        status.put("perType", types);
        return status;
    }

    public void updateGlobal(int capacity, double refillPerSec) {
        this.globalCapacity = capacity;
        this.globalRefillPerSec = refillPerSec;
        this.globalBucket = new TokenBucket(capacity, refillPerSec);
    }

    public void updatePerType(int capacity, double refillPerSec) {
        this.perTypeCapacity = capacity;
        this.perTypeRefillPerSec = refillPerSec;
        for (TaskType type : TaskType.values()) {
            typeBuckets.put(type, new TokenBucket(capacity, refillPerSec));
        }
    }

    //Token Bucket

    private static class TokenBucket {
        private final int capacity;
        private final double refillPerSec;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, double refillPerSec) {
            this.capacity = capacity;
            this.refillPerSec = refillPerSec;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsed * refillPerSec);
            lastRefillNanos = now;
        }

        Map<String, Object> getStatus() {
            refill();
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("capacity", capacity);
            s.put("refillPerSec", refillPerSec);
            s.put("availableTokens", Math.round(tokens * 10.0) / 10.0);
            return s;
        }
    }
}
