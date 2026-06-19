package com.lumira.common.web.repeatsubmit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RepeatSubmitStore {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, Instant> localKeys = new ConcurrentHashMap<>();

    public RepeatSubmitStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public boolean putIfAbsent(String key, Duration ttl) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        }

        Instant now = Instant.now();
        localKeys.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        Instant expiresAt = now.plus(ttl);
        Instant existing = localKeys.putIfAbsent(key, expiresAt);
        return existing == null || !existing.isAfter(now);
    }

    public void remove(String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            redisTemplate.delete(key);
            return;
        }
        localKeys.remove(key);
    }
}
