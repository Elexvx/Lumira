package com.lumira.saas.modules.system.online;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.common.constant.CacheKeyConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OnlineSessionEventPublisherTest {

    @Test
    void publishShouldRejectInvalidEventBeforeRedisAccess() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        OnlineSessionEvent event = trustedEvent();
        event.setSessionId("../session");

        assertThrows(IllegalArgumentException.class,
                () -> new OnlineSessionEventPublisher(redisTemplate, new ObjectMapper()).publish(event));

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void publishShouldRejectIdentityEventWithoutUserUuidBeforeRedisAccess() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        OnlineSessionEvent event = trustedEvent();
        event.setUserUuid(null);

        assertThrows(IllegalArgumentException.class,
                () -> new OnlineSessionEventPublisher(redisTemplate, new ObjectMapper()).publish(event));

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void publishShouldSendTrustedEvent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        new OnlineSessionEventPublisher(redisTemplate, new ObjectMapper()).publish(trustedEvent());

        verify(redisTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq(CacheKeyConstants.onlineSessionEventsChannel()), anyString());
    }

    @Test
    void publishShouldAllowHeartbeatWithoutUserIdentity() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(OnlineSessionEvent.ACTION_HEARTBEAT);

        new OnlineSessionEventPublisher(redisTemplate, new ObjectMapper()).publish(event);

        verify(redisTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq(CacheKeyConstants.onlineSessionEventsChannel()), anyString());
    }

    private OnlineSessionEvent trustedEvent() {
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(OnlineSessionEvent.ACTION_UPSERT);
        event.setUserId(1001L);
        event.setUserUuid("user-uuid-1");
        event.setSessionId("session-1");
        event.setOperatorUsername("alice");
        return event;
    }
}
