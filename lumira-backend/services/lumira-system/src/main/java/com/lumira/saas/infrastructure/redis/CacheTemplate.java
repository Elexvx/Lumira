package com.lumira.saas.infrastructure.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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

    public boolean putIfAbsent(String key, String value, Duration ttl) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(result);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public String getAndRemove(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    public List<String> multiGet(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return redisTemplate.opsForValue().multiGet(keys);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public void remove(String key) {
        redisTemplate.delete(key);
    }

    public void remove(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }

    public Set<String> scan(String pattern) {
        Set<String> results = new LinkedHashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Redis SCAN 读取失败", ex);
        }
        return results;
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
