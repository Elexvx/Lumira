package com.lumira.saas.modules.plugin.event;

import com.lumira.domain.event.StandardDomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PluginDomainEventPublisherTest {

    @Test
    void publishShouldNotInventAuditUserWhenEventHasNoActor() {
        PluginOutboxService outboxService = mock(PluginOutboxService.class);
        PluginDomainEventPublisher publisher = new PluginDomainEventPublisher(outboxService);
        var event = StandardDomainEvent.of(
                "PLUGIN_ENABLED",
                "plugin.activation",
                "sensitive-words",
                Map.of("version", "1.0.0")
        );

        publisher.publish(event);

        verify(outboxService).recordAfterCommit(
                eq(null),
                eq("PLUGIN_ENABLED"),
                anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void publishShouldCarryTrustedActorSnapshotInEnvelope() {
        PluginOutboxService outboxService = mock(PluginOutboxService.class);
        PluginDomainEventPublisher publisher = new PluginDomainEventPublisher(outboxService);
        var event = StandardDomainEvent.of(
                "PLUGIN_ENABLED",
                "plugin.activation",
                "sensitive-words",
                Map.of("version", "1.0.0", "userId", 1001L, "userUuid", "user-uuid-1001")
        );
        ArgumentCaptor<Object> payloadCaptor = forClass(Object.class);

        publisher.publish(event);

        verify(outboxService).recordAfterCommit(
                eq(1001L),
                eq("PLUGIN_ENABLED"),
                anyString(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOfSatisfying(Map.class, payload -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attributes = (Map<String, Object>) payload.get("attributes");
                    assertThat(attributes)
                            .containsEntry("userId", 1001L)
                            .containsEntry("userUuid", "user-uuid-1001");
                });
    }
}
