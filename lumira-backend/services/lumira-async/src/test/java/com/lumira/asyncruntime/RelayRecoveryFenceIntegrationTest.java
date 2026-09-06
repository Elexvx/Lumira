package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import com.lumira.api.event.RelayExecutionContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves that recovery takeover fences an already queued async generation. */
class RelayRecoveryFenceIntegrationTest {

    @Test
    void oldGenerationCannotPublishAfterRecoveryTakeover() {
        AtomicInteger publishCount = new AtomicInteger();
        OwnerOutboxRelayPort relay = new OwnerOutboxRelayPort() {
            @Override
            public String owner() {
                return "platform";
            }

            @Override
            public int dispatchPendingEvents() {
                return 0;
            }

            @Override
            public boolean replay(Long eventId) {
                return false;
            }

            @Override
            public int dispatchPendingEvents(RelayExecutionContext context) {
                publishCount.incrementAndGet();
                return 1;
            }
        };
        RecoveryFenceRegistry fences = new RecoveryFenceRegistry();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        OwnerRelayLaneProperties properties = new OwnerRelayLaneProperties();
        properties.setRetryBudget(0);
        OwnerRelayLane lane = new OwnerRelayLane(
                relay,
                properties.settingsFor("platform"),
                meters,
                new AsyncRuntimeDrainCoordinator(),
                fences
        );

        try {
            RecoveryFenceRegistry.RelayFenceToken generationOne =
                    fences.acquireOrRenew("platform", "lumira-async");
            assertThat(lane.dispatch(generationOne.context()).join()).isEqualTo(1);

            RecoveryFenceRegistry.RelayFenceToken generationTwo =
                    fences.takeover("platform", "job-recovery");
            assertThat(generationTwo.generation()).isEqualTo(generationOne.generation() + 1L);

            assertThatThrownBy(() -> lane.dispatch(generationOne.context()).join())
                    .isInstanceOf(CompletionException.class)
                    .hasRootCauseInstanceOf(RecoveryFenceRegistry.FenceLostException.class);
            assertThat(publishCount).hasValue(1);
            assertThat(meters.get("lumira.async.fence.reject.count")
                    .tag("owner", "platform")
                    .tag("operation", "relay")
                    .counter()
                    .count()).isEqualTo(1.0d);
        } finally {
            lane.close();
        }
    }

    @Test
    void normalAsyncHolderCannotDisplaceLiveRecoveryHolder() {
        RecoveryFenceRegistry fences = new RecoveryFenceRegistry();

        RecoveryFenceRegistry.RelayFenceToken normal = fences.acquireOrRenew("file", "lumira-async");
        RecoveryFenceRegistry.RelayFenceToken recovery = fences.takeover("file", "job-recovery");

        assertThat(recovery.generation()).isEqualTo(normal.generation() + 1L);
        assertThatThrownBy(() -> fences.acquireOrRenew("file", "lumira-async"))
                .isInstanceOf(RecoveryFenceRegistry.FenceOwnershipException.class);
    }
}
