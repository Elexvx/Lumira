package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Low-latency owner relay loop. XXL-JOB remains a recovery trigger, but normal
 * publication no longer waits for an external HTTP scheduling round trip.
 */
@Component
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.relay-loop", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayCoordinator {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayCoordinator.class);

    private final Map<String, OwnerRelayLane> ownerLanes;
    private final MeterRegistry meterRegistry;
    private final Duration completionTimeout;

    public OutboxRelayCoordinator(
            List<OwnerOutboxRelayPort> ownerRelays,
            MeterRegistry meterRegistry
    ) {
        this(ownerRelays, meterRegistry, new AsyncRuntimeDrainCoordinator(), new OwnerRelayLaneProperties());
    }

    @Autowired
    public OutboxRelayCoordinator(
            List<OwnerOutboxRelayPort> ownerRelays,
            MeterRegistry meterRegistry,
            AsyncRuntimeDrainCoordinator drainCoordinator,
            OwnerRelayLaneProperties properties
    ) {
        this.meterRegistry = meterRegistry;
        this.completionTimeout = properties.completionTimeout();
        Map<String, OwnerRelayLane> lanes = new LinkedHashMap<>();
        for (OwnerOutboxRelayPort relay : ownerRelays == null ? List.<OwnerOutboxRelayPort>of() : ownerRelays) {
            OwnerRelayLane lane = new OwnerRelayLane(relay, properties.settingsFor(relay.owner()), meterRegistry, drainCoordinator);
            if (lanes.putIfAbsent(lane.owner(), lane) != null) {
                lane.close();
                throw new IllegalArgumentException("Duplicate owner relay: " + relay.owner());
            }
        }
        this.ownerLanes = Map.copyOf(lanes);
    }

    @Scheduled(
            initialDelayString = "${lumira.event.relay-loop.initial-delay-ms:3000}",
            fixedDelayString = "${lumira.event.relay-loop.fixed-delay-ms:500}"
    )
    public void relay() {
        relayNow();
    }

    /** Invoked by the scheduled loop and the job-executor compatibility endpoint. */
    public int relayNow() {
        Map<String, CompletableFuture<Integer>> pending = new LinkedHashMap<>();
        ownerLanes.forEach((owner, lane) -> pending.put(owner, lane.dispatch()));
        int total = 0;
        for (Map.Entry<String, CompletableFuture<Integer>> entry : pending.entrySet()) {
            total += await(entry.getKey(), entry.getValue());
        }
        return total;
    }

    public int recoverOwner(String owner) {
        OwnerRelayLane lane = requireOwner(owner);
        return await(owner, lane.dispatch());
    }

    public boolean replay(String owner, long eventId) {
        if (eventId <= 0L) throw new IllegalArgumentException("eventId must be positive");
        OwnerRelayLane lane = requireOwner(owner);
        return await(owner, lane.replay(eventId)) > 0;
    }

    private OwnerRelayLane requireOwner(String owner) {
        OwnerRelayLane lane = ownerLanes.get(owner);
        if (lane == null) throw new IllegalArgumentException("Unknown outbox owner: " + owner);
        return lane;
    }

    private int await(String owner, CompletableFuture<Integer> future) {
        try {
            return Math.max(0, future.get(completionTimeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException exception) {
            meterRegistry.counter("lumira.event.relay.coordinator.timeout", "owner", owner).increment();
            log.warn("outbox relay completion timed out owner={}", owner);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            meterRegistry.counter("lumira.event.relay.coordinator.interrupted", "owner", owner).increment();
        } catch (ExecutionException exception) {
            log.warn("outbox relay failed owner={}: {}", owner, exception.getMessage());
        }
        return 0;
    }

    @PreDestroy
    void closeLanes() {
        ownerLanes.values().forEach(OwnerRelayLane::close);
    }
}
