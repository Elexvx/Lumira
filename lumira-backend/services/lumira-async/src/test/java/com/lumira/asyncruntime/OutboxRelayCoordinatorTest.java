package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxRelayCoordinatorTest {

    @Test
    void delegatesRelayToOwnerPortsAndKeepsFailuresIsolated() {
        OwnerOutboxRelayPort platform = mock(OwnerOutboxRelayPort.class);
        OwnerOutboxRelayPort file = mock(OwnerOutboxRelayPort.class);
        when(platform.owner()).thenReturn("platform");
        when(platform.dispatchPendingEvents()).thenReturn(3);
        when(file.owner()).thenReturn("file");
        when(file.dispatchPendingEvents()).thenThrow(new IllegalStateException("control plane unavailable"));
        SimpleMeterRegistry meters = new SimpleMeterRegistry();

        int relayed = new OutboxRelayCoordinator(List.of(platform, file), meters).relayNow();

        assertThat(relayed).isEqualTo(3);
        assertThat(meters.get("lumira.event.relay.published").tag("owner", "platform").counter().count()).isEqualTo(3.0d);
        assertThat(meters.get("lumira.event.relay.failure").tag("owner", "file").counter().count()).isEqualTo(1.0d);
    }
}
