package com.lumira.common.web.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    @Test
    void allowsUntilLocalLimitAndRejectsAfterLimit() {
        RateLimitService service = new RateLimitService(emptyRedisProvider());
        RateLimitRule rule = new RateLimitRule("login", 2, 60);

        assertThat(service.check("login:ip:127.0.0.1", rule).allowed()).isTrue();
        assertThat(service.check("login:ip:127.0.0.1", rule).allowed()).isTrue();
        RateLimitResult third = service.check("login:ip:127.0.0.1", rule);

        assertThat(third.allowed()).isFalse();
        assertThat(third.count()).isEqualTo(3);
        assertThat(third.retryAfterSeconds()).isPositive();
    }

    @Test
    void assertAllowedThrowsGenericExceptionWithoutKeyDetails() {
        RateLimitService service = new RateLimitService(emptyRedisProvider());
        RateLimitRule rule = new RateLimitRule("webhook", 1, 60);

        service.assertAllowed("webhook:secret-key", rule);

        assertThatThrownBy(() -> service.assertAllowed("webhook:secret-key", rule))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Request rate limited")
                .hasMessageNotContaining("secret-key");
    }

    private ObjectProvider<StringRedisTemplate> emptyRedisProvider() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
