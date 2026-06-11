package com.lumira.saas.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStartupCleanupRunnerTest {

    @Test
    void skipsCleanupByDefault() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisStartupCleanupRunner runner = new RedisStartupCleanupRunner(
                redisTemplate,
                new RedisStartupCleanupProperties(),
                new MockEnvironment()
        );

        runner.run(new DefaultApplicationArguments());

        verify(redisTemplate, never()).execute(any(RedisCallback.class));
    }

    @Test
    void blocksCleanupInProdWithoutExplicitOverride() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisStartupCleanupProperties properties = new RedisStartupCleanupProperties();
        properties.setClearOnStartup(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        RedisStartupCleanupRunner runner = new RedisStartupCleanupRunner(redisTemplate, properties, environment);

        runner.run(new DefaultApplicationArguments());

        verify(redisTemplate, never()).execute(any(RedisCallback.class));
    }

    @Test
    void flushesCurrentDatabaseOutsideProdWhenEnabled() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisServerCommands serverCommands = mock(RedisServerCommands.class);
        when(connection.serverCommands()).thenReturn(serverCommands);
        StringRedisTemplate redisTemplate = redisTemplateWithConnection(connection);
        RedisStartupCleanupProperties properties = new RedisStartupCleanupProperties();
        properties.setClearOnStartup(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        RedisStartupCleanupRunner runner = new RedisStartupCleanupRunner(redisTemplate, properties, environment);

        runner.run(new DefaultApplicationArguments());

        verify(serverCommands).flushDb();
    }

    @Test
    void doesNotFailStartupWhenRedisCleanupThrows() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(new RedisConnectionFailureException("down"));
        RedisStartupCleanupProperties properties = new RedisStartupCleanupProperties();
        properties.setClearOnStartup(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        RedisStartupCleanupRunner runner = new RedisStartupCleanupRunner(redisTemplate, properties, environment);

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));
    }

    private StringRedisTemplate redisTemplateWithConnection(RedisConnection connection) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        return redisTemplate;
    }
}
