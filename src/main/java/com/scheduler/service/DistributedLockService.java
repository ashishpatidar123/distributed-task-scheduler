package com.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;


@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private final RedisTemplate<String, Object> redisTemplate;

    public DistributedLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String key, String owner, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent("lock:" + key, owner, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Lock acquired: {} by {}", key, owner);
        }
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String key, String owner) {
        Object current = redisTemplate.opsForValue().get("lock:" + key);
        if (owner.equals(current)) {
            redisTemplate.delete("lock:" + key);
            log.debug("Lock released: {} by {}", key, owner);
        }
    }
}
