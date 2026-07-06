package com.lumira.saas.infrastructure.event.domain;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemDomainEventPublisherTest {

    @Test
    void publishShouldPromoteTrustedActorFromAttributes() {
        PlatformEventPublisher platformEventPublisher = mock(PlatformEventPublisher.class);
        SystemDomainEventPublisher publisher = new SystemDomainEventPublisher(platformEventPublisher);
        var event = StandardDomainEvent.of(
                "PLATFORM_CONFIG_CHANGED",
                "platform.config",
                "3001",
                Map.of("configKey", "site.name", "userId", 2001L, "userUuid", "user-uuid-2001")
        );
        ArgumentCaptor<Map<String, Object>> attributesCaptor = mapCaptor();

        publisher.publish(event);

        verify(platformEventPublisher).publishAfterCommit(
                eq(PlatformEventTypes.SOURCE_SYSTEM),
                eq("PLATFORM_CONFIG_CHANGED"),
                eq(2001L),
                eq("platform.config"),
                eq(3001L),
                attributesCaptor.capture()
        );
        assertThat(attributesCaptor.getValue())
                .containsEntry("userId", 2001L)
                .containsEntry("userUuid", "user-uuid-2001");
    }

    @Test
    void publishShouldNotInventAuditUserWhenEventHasNoActor() {
        PlatformEventPublisher platformEventPublisher = mock(PlatformEventPublisher.class);
        SystemDomainEventPublisher publisher = new SystemDomainEventPublisher(platformEventPublisher);
        var event = StandardDomainEvent.of(
                "PLATFORM_CONFIG_CHANGED",
                "platform.config",
                "3001",
                Map.of("configKey", "site.name")
        );

        publisher.publish(event);

        verify(platformEventPublisher).publishAfterCommit(
                eq(PlatformEventTypes.SOURCE_SYSTEM),
                eq("PLATFORM_CONFIG_CHANGED"),
                eq(null),
                eq("platform.config"),
                eq(3001L),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) forClass(Map.class);
    }
}
