package com.lumira.saas.infrastructure.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(RedisStartupCleanupProperties.class)
public class RedisConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    /**
     * Cache data gets a different physical Redis only when the deployment
     * explicitly enables the cache plane. Keeping the fallback wired to the
     * runtime template preserves local single-Redis development while making
     * production isolation observable and fail-closed when requested.
     */
    @Bean(name = "cacheRedisConnectionFactory")
    @ConditionalOnExpression("${REDIS_CACHE_ENABLED:false}")
    public RedisConnectionFactory cacheRedisConnectionFactory(
            @Value("${REDIS_CACHE_HOST:localhost}") String host,
            @Value("${REDIS_CACHE_PORT:6379}") int port,
            @Value("${REDIS_CACHE_PASSWORD:}") String password
    ) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        if (StringUtils.hasText(password)) {
            configuration.setPassword(RedisPassword.of(password));
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean(name = "cacheRedisTemplate")
    public StringRedisTemplate cacheRedisTemplate(
            @Qualifier("cacheRedisConnectionFactory") ObjectProvider<RedisConnectionFactory> cacheConnectionFactory,
            @Qualifier("stringRedisTemplate") StringRedisTemplate runtimeRedisTemplate
    ) {
        RedisConnectionFactory dedicatedFactory = cacheConnectionFactory.getIfAvailable();
        return dedicatedFactory == null ? runtimeRedisTemplate : new StringRedisTemplate(dedicatedFactory);
    }
}
