package com.lumira.saas.infrastructure.redis;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Exposes runtime/cache Redis reachability and physical isolation. */
@Component("redisPlane")
public class RedisPlaneHealthIndicator implements HealthIndicator {
    private final StringRedisTemplate runtimeRedis;
    private final StringRedisTemplate cacheRedis;
    private final boolean cacheIsolationRequired;
    private final MeterRegistry meters;
    private final AtomicInteger runtimeAvailable = new AtomicInteger();
    private final AtomicInteger cacheAvailable = new AtomicInteger();
    private final AtomicInteger physicallyIsolated = new AtomicInteger();
    private final AtomicLong runtimeEvictedKeys = new AtomicLong(-1L);
    private final AtomicLong cacheEvictedKeys = new AtomicLong(-1L);

    public RedisPlaneHealthIndicator(
            @Qualifier("stringRedisTemplate") StringRedisTemplate runtimeRedis,
            @Qualifier("cacheRedisTemplate") StringRedisTemplate cacheRedis,
            @Value("${REDIS_CACHE_ENABLED:false}") boolean cacheIsolationRequired,
            ObjectProvider<MeterRegistry> meterProvider
    ) {
        this.runtimeRedis = runtimeRedis;
        this.cacheRedis = cacheRedis;
        this.cacheIsolationRequired = cacheIsolationRequired;
        this.meters = meterProvider.getIfAvailable();
        if (meters != null) {
            Gauge.builder("lumira.redis.plane.available", runtimeAvailable, AtomicInteger::get)
                    .tag("plane", "runtime")
                    .register(meters);
            Gauge.builder("lumira.redis.plane.available", cacheAvailable, AtomicInteger::get)
                    .tag("plane", "cache")
                    .register(meters);
            Gauge.builder("lumira.redis.plane.isolated", physicallyIsolated, AtomicInteger::get)
                    .register(meters);
            Gauge.builder("redis_runtime_evicted_keys", runtimeEvictedKeys, AtomicLong::get)
                    .description("Redis runtime evicted key count; any positive value fails readiness.")
                    .register(meters);
            Gauge.builder("redis_cache_evicted_keys", cacheEvictedKeys, AtomicLong::get)
                    .description("Redis cache evicted key count; cache eviction is expected to be rebuildable.")
                    .register(meters);
        }
    }

    @Override
    public Health health() {
        PlaneProbe runtime = probe(runtimeRedis, "runtime");
        PlaneProbe cache = probe(cacheRedis, "cache");
        boolean runtimeUp = runtime.available();
        boolean cacheUp = cache.available();
        boolean isolated = physicallyIsolated();
        runtimeAvailable.set(runtimeUp ? 1 : 0);
        cacheAvailable.set(cacheUp ? 1 : 0);
        physicallyIsolated.set(isolated ? 1 : 0);
        runtimeEvictedKeys.set(runtime.evictedKeys());
        cacheEvictedKeys.set(cache.evictedKeys());

        Health.Builder builder;
        if (!runtimeUp || !runtime.statsAvailable() || runtime.evictedKeys() > 0L) {
            builder = Health.down();
        } else if (cacheIsolationRequired && (!cacheUp || !isolated || !cache.statsAvailable())) {
            builder = Health.status("DEGRADED");
        } else {
            builder = Health.up();
        }
        return builder
                .withDetail("runtime", runtimeUp ? "UP" : "DOWN")
                .withDetail("cache", cacheUp ? "UP" : "DOWN")
                .withDetail("cacheIsolationRequired", cacheIsolationRequired)
                .withDetail("physicalIsolation", isolated ? "DEDICATED" : "SHARED_RUNTIME_FALLBACK")
                .withDetail("runtimeEvictedKeys", runtime.evictedKeys())
                .withDetail("cacheEvictedKeys", cache.evictedKeys())
                .withDetail("runtimeStatsAvailable", runtime.statsAvailable())
                .withDetail("cacheStatsAvailable", cache.statsAvailable())
                .build();
    }

    private PlaneProbe probe(StringRedisTemplate template, String plane) {
        if (template == null || template.getConnectionFactory() == null) {
            incrementFailure(plane);
            return new PlaneProbe(false, false, -1L);
        }
        try (RedisConnection connection = template.getConnectionFactory().getConnection()) {
            String response = connection.ping();
            boolean available = "PONG".equalsIgnoreCase(response);
            if (!available) {
                incrementFailure(plane);
                return new PlaneProbe(false, false, -1L);
            }
            Long evictedKeys = readEvictedKeys(connection.serverCommands());
            return new PlaneProbe(true, evictedKeys != null, evictedKeys == null ? -1L : evictedKeys);
        } catch (RuntimeException exception) {
            incrementFailure(plane);
            return new PlaneProbe(false, false, -1L);
        }
    }

    private Long readEvictedKeys(RedisServerCommands serverCommands) {
        if (serverCommands == null) {
            return null;
        }
        Properties info = serverCommands.info();
        if (info == null || !info.containsKey("evicted_keys")) {
            return null;
        }
        try {
            return Long.parseLong(info.getProperty("evicted_keys"));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void incrementFailure(String plane) {
        if (meters != null) {
            meters.counter("lumira.redis.plane.ping.failure", "plane", plane).increment();
        }
    }

    private boolean physicallyIsolated() {
        if (runtimeRedis == null || cacheRedis == null) {
            return false;
        }
        var runtimeFactory = runtimeRedis.getConnectionFactory();
        var cacheFactory = cacheRedis.getConnectionFactory();
        return runtimeFactory != null && cacheFactory != null && runtimeFactory != cacheFactory;
    }

    private record PlaneProbe(boolean available, boolean statsAvailable, long evictedKeys) {
    }
}
