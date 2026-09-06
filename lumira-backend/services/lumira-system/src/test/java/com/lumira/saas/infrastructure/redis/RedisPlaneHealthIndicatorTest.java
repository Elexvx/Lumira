package com.lumira.saas.infrastructure.redis;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisPlaneHealthIndicatorTest {

    @Test
    void reportsDedicatedCachePlaneAndAvailabilityMetrics() {
        RedisConnection runtimeConnection = mock(RedisConnection.class);
        RedisConnection cacheConnection = mock(RedisConnection.class);
        when(runtimeConnection.ping()).thenReturn("PONG");
        when(cacheConnection.ping()).thenReturn("PONG");
        RedisConnectionFactory runtimeFactory = mock(RedisConnectionFactory.class);
        RedisConnectionFactory cacheFactory = mock(RedisConnectionFactory.class);
        when(runtimeFactory.getConnection()).thenReturn(runtimeConnection);
        when(cacheFactory.getConnection()).thenReturn(cacheConnection);
        StringRedisTemplate runtime = template(runtimeFactory);
        StringRedisTemplate cache = template(cacheFactory);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = provider(meters);

        var health = new RedisPlaneHealthIndicator(runtime, cache, true, provider).health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("physicalIsolation", "DEDICATED");
        assertThat(meters.get("lumira.redis.plane.isolated").gauge().value()).isEqualTo(1.0d);
    }

    @Test
    void degradesWhenProductionRequiresIsolationButCacheFallsBackToRuntime() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.ping()).thenReturn("PONG");
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        StringRedisTemplate runtime = template(factory);
        StringRedisTemplate cache = template(factory);

        var health = new RedisPlaneHealthIndicator(
                runtime,
                cache,
                true,
                provider(new SimpleMeterRegistry())
        ).health();

        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getDetails()).containsEntry("physicalIsolation", "SHARED_RUNTIME_FALLBACK");
    }

    private static StringRedisTemplate template(RedisConnectionFactory factory) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        return template;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> provider(MeterRegistry meters) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(meters);
        return provider;
    }
}
