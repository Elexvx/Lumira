package com.yourcompany.saas.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Component
public class CacheTemplate {

    private final StringRedisTemplate redisTemplate;

    public CacheTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void remove(String key) {
        redisTemplate.delete(key);
    }

    public void addToSortedSet(String key, String value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    public void addToSortedSet(String key, Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        ZSetOperations<String, String> zSetOperations = redisTemplate.opsForZSet();
        values.forEach((value, score) -> zSetOperations.add(key, value, score));
    }

    public Set<String> reverseRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    public Long sortedSetSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    public Long removeRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    public void removeFromSortedSet(String key, String... values) {
        if (values == null || values.length == 0) {
            return;
        }
        redisTemplate.opsForZSet().remove(key, (Object[]) values);
    }
}
