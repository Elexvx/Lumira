package com.lumira.common.web.security.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();

    public RateLimitService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public RateLimitResult check(String key, RateLimitRule rule) {
        if (!StringUtils.hasText(key) || rule == null || rule.maxAttempts() <= 0 || rule.windowSeconds() <= 0) {
            return new RateLimitResult(true, 0, 0);
        }
        String redisKey = "lumira:rate-limit:" + sanitizeKey(key);
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                Long count = redisTemplate.opsForValue().increment(redisKey);
                if (count != null && count == 1L) {
                    redisTemplate.expire(redisKey, Duration.ofSeconds(rule.windowSeconds()));
                }
                Long ttl = redisTemplate.getExpire(redisKey);
                long retryAfter = ttl == null || ttl < 0 ? rule.windowSeconds() : ttl;
                return new RateLimitResult(count == null || count <= rule.maxAttempts(), count == null ? 0 : count, retryAfter);
            } catch (RuntimeException ignored) {
                // Fall back locally when Redis is temporarily unavailable.
            }
        }
        return checkLocal(redisKey, rule);
    }

    public void assertAllowed(String key, RateLimitRule rule) {
        RateLimitResult result = check(key, rule);
        if (!result.allowed()) {
            throw new RateLimitExceededException();
        }
    }

    private RateLimitResult checkLocal(String key, RateLimitRule rule) {
        long now = Instant.now().getEpochSecond();
        if (localBuckets.size() > 10_000) {
            cleanup(now);
        }
        LocalBucket bucket = localBuckets.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt <= now) {
                return new LocalBucket(1, now + rule.windowSeconds());
            }
            current.count++;
            return current;
        });
        long retryAfter = Math.max(1, bucket.expiresAt - now);
        return new RateLimitResult(bucket.count <= rule.maxAttempts(), bucket.count, retryAfter);
    }

    private void cleanup(long now) {
        Iterator<Map.Entry<String, LocalBucket>> iterator = localBuckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt <= now) {
                iterator.remove();
            }
        }
    }

    private String sanitizeKey(String key) {
        return key.replaceAll("[^A-Za-z0-9:._-]", "_");
    }

    private static final class LocalBucket {
        private long count;
        private final long expiresAt;

        private LocalBucket(long count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
