package com.lumira.saas.infrastructure.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
public class RedisStartupCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisStartupCleanupRunner.class);
    private static final Set<String> PROD_PROFILES = Set.of("prod", "production");

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisStartupCleanupProperties properties;
    private final Environment environment;

    public RedisStartupCleanupRunner(
            StringRedisTemplate stringRedisTemplate,
            RedisStartupCleanupProperties properties,
            Environment environment
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Redis startup cleanup skipped: redis integration disabled");
            return;
        }
        if (!properties.isClearOnStartup()) {
            log.info("Redis startup cleanup skipped: clear-on-startup is disabled");
            return;
        }
        if (isProdProfileActive() && !properties.isAllowClearOnStartupInProd()) {
            log.warn("Redis startup cleanup blocked: prod profile requires allow-clear-on-startup-in-prod=true");
            return;
        }

        try {
            log.warn("Redis startup cleanup started: flushing current configured Redis database");
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.serverCommands().flushDb();
                return null;
            });
            log.warn("Redis startup cleanup completed: current configured Redis database flushed");
        } catch (RedisConnectionFailureException exception) {
            log.warn("Redis startup cleanup skipped: Redis connection failed ({})", exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            log.warn("Redis startup cleanup failed: application startup will continue ({})", exception.getClass().getSimpleName());
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> PROD_PROFILES.contains(profile.toLowerCase()));
    }
}
