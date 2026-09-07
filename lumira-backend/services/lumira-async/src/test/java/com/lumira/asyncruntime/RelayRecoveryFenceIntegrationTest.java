package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import com.lumira.api.event.RelayExecutionContext;
import com.lumira.common.web.internal.RelayFenceValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void staleAsyncRequestIsRejectedAtOwnerBoundaryBeforePublish() {
        StringRedisTemplate runtimeRedis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> fenceHash = mock(HashOperations.class);
        AtomicReference<OwnerFenceState> ownerFence = new AtomicReference<>();
        String fenceKey = "lumira:runtime:recovery-fence:payment";
        when(runtimeRedis.opsForHash()).thenReturn(fenceHash);
        when(fenceHash.get(eq(fenceKey), eq("relay_generation")))
                .thenAnswer(ignored -> Long.toString(ownerFence.get().generation()));
        when(fenceHash.get(eq(fenceKey), eq("relay_digest")))
                .thenAnswer(ignored -> ownerFence.get().digest());
        when(fenceHash.get(eq(fenceKey), eq("relay_lease_until")))
                .thenAnswer(ignored -> Long.toString(ownerFence.get().leaseUntilMillis()));

        RecoveryFenceRegistry fences = new RecoveryFenceRegistry();
        RecoveryFenceRegistry.RelayFenceToken asyncGenerationOne =
                fences.acquireOrRenew("payment", "lumira-async");
        ownerFence.set(OwnerFenceState.from(asyncGenerationOne));

        AtomicInteger publishCount = new AtomicInteger();
        java.util.function.Consumer<RelayExecutionContext> ownerEndpoint = context -> {
            RelayFenceValidator.assertCurrent(
                    runtimeRedis,
                    "payment",
                    context.owner(),
                    context.generation(),
                    context.fenceToken()
            );
            publishCount.incrementAndGet();
        };

        ownerEndpoint.accept(asyncGenerationOne.context());
        assertThat(publishCount).hasValue(1);

        RecoveryFenceRegistry.RelayFenceToken jobGenerationTwo =
                fences.takeover("payment", "job-recovery");
        assertThat(jobGenerationTwo.generation()).isEqualTo(asyncGenerationOne.generation() + 1L);
        ownerFence.set(OwnerFenceState.from(jobGenerationTwo));

        assertThatThrownBy(() -> ownerEndpoint.accept(asyncGenerationOne.context()))
                .isInstanceOf(RelayFenceValidator.StaleRelayFenceException.class);
        assertThat(publishCount).hasValue(1);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record OwnerFenceState(long generation, String digest, long leaseUntilMillis) {
        private static OwnerFenceState from(RecoveryFenceRegistry.RelayFenceToken token) {
            return new OwnerFenceState(
                    token.generation(),
                    RelayRecoveryFenceIntegrationTest.digest(token.fenceToken()),
                    System.currentTimeMillis() + 60_000L
            );
        }
    }
}
