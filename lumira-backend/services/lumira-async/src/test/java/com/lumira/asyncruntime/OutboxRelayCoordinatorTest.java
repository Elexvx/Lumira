package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

    @Test
    void slowOwnerDoesNotPreventAnotherOwnerLaneFromCompleting() throws Exception {
        CountDownLatch releaseSlowOwner = new CountDownLatch(1);
        OwnerOutboxRelayPort slow = mock(OwnerOutboxRelayPort.class);
        OwnerOutboxRelayPort fast = mock(OwnerOutboxRelayPort.class);
        when(slow.owner()).thenReturn("platform");
        when(slow.dispatchPendingEvents()).thenAnswer(ignored -> {
            releaseSlowOwner.await(5, TimeUnit.SECONDS);
            return 1;
        });
        when(fast.owner()).thenReturn("file");
        when(fast.dispatchPendingEvents()).thenReturn(2);
        OwnerRelayLaneProperties properties = new OwnerRelayLaneProperties();
        properties.setRetryBudget(0);
        properties.setCompletionTimeout(Duration.ofMillis(75));
        OutboxRelayCoordinator coordinator = new OutboxRelayCoordinator(
                List.of(slow, fast), new SimpleMeterRegistry(), new AsyncRuntimeDrainCoordinator(), properties
        );

        try {
            assertThat(coordinator.relayNow()).isEqualTo(2);
        } finally {
            releaseSlowOwner.countDown();
            coordinator.closeLanes();
        }
    }

    @Test
    void opensOwnerCircuitWithoutSuppressingOtherOwners() {
        OwnerOutboxRelayPort broken = mock(OwnerOutboxRelayPort.class);
        OwnerOutboxRelayPort healthy = mock(OwnerOutboxRelayPort.class);
        when(broken.owner()).thenReturn("platform");
        when(broken.dispatchPendingEvents()).thenThrow(new IllegalStateException("down"));
        when(healthy.owner()).thenReturn("file");
        when(healthy.dispatchPendingEvents()).thenReturn(1);
        OwnerRelayLaneProperties properties = new OwnerRelayLaneProperties();
        properties.setRetryBudget(0);
        properties.setCircuitFailureThreshold(1);
        OutboxRelayCoordinator coordinator = new OutboxRelayCoordinator(
                List.of(broken, healthy), new SimpleMeterRegistry(), new AsyncRuntimeDrainCoordinator(), properties
        );

        try {
            assertThat(coordinator.relayNow()).isEqualTo(1);
            assertThat(coordinator.relayNow()).isEqualTo(1);
            verify(broken, times(1)).dispatchPendingEvents();
            verify(healthy, times(2)).dispatchPendingEvents();
        } finally {
            coordinator.closeLanes();
        }
    }
}
