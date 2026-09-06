package com.lumira.saas.infrastructure.redis;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

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
        }
    }

    @Override
    public Health health() {
        boolean runtimeUp = ping(runtimeRedis, "runtime");
        boolean cacheUp = ping(cacheRedis, "cache");
        boolean isolated = runtimeRedis.getConnectionFactory() != cacheRedis.getConnectionFactory();
        runtimeAvailable.set(runtimeUp ? 1 : 0);
        cacheAvailable.set(cacheUp ? 1 : 0);
        physicallyIsolated.set(isolated ? 1 : 0);

        Health.Builder builder;
        if (!runtimeUp) {
            builder = Health.down();
        } else if (cacheIsolationRequired && (!cacheUp || !isolated)) {
            builder = Health.status("DEGRADED");
        } else {
            builder = Health.up();
        }
        return builder
                .withDetail("runtime", runtimeUp ? "UP" : "DOWN")
                .withDetail("cache", cacheUp ? "UP" : "DOWN")
                .withDetail("cacheIsolationRequired", cacheIsolationRequired)
                .withDetail("physicalIsolation", isolated ? "DEDICATED" : "SHARED_RUNTIME_FALLBACK")
                .build();
    }

    private boolean ping(StringRedisTemplate template, String plane) {
        if (template == null || template.getConnectionFactory() == null) {
            incrementFailure(plane);
            return false;
        }
        try (RedisConnection connection = template.getConnectionFactory().getConnection()) {
            String response = connection.ping();
            boolean available = "PONG".equalsIgnoreCase(response);
            if (!available) incrementFailure(plane);
            return available;
        } catch (RuntimeException exception) {
            incrementFailure(plane);
            return false;
        }
    }

    private void incrementFailure(String plane) {
        if (meters != null) {
            meters.counter("lumira.redis.plane.ping.failure", "plane", plane).increment();
        }
    }
}
