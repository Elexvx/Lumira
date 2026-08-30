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

import java.util.List;

/**
 * Low-latency owner relay loop. XXL-JOB remains a recovery trigger, but normal
 * publication no longer waits for an external HTTP scheduling round trip.
 */
@Component
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.relay-loop", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayCoordinator {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayCoordinator.class);

    private final List<OwnerOutboxRelayPort> ownerRelays;
    private final MeterRegistry meterRegistry;
    private final AsyncRuntimeDrainCoordinator drainCoordinator;

    public OutboxRelayCoordinator(
            List<OwnerOutboxRelayPort> ownerRelays,
            MeterRegistry meterRegistry
    ) {
        this(ownerRelays, meterRegistry, new AsyncRuntimeDrainCoordinator());
    }

    @Autowired
    public OutboxRelayCoordinator(
            List<OwnerOutboxRelayPort> ownerRelays,
            MeterRegistry meterRegistry,
            AsyncRuntimeDrainCoordinator drainCoordinator
    ) {
        this.ownerRelays = ownerRelays == null ? List.of() : List.copyOf(ownerRelays);
        this.meterRegistry = meterRegistry;
        this.drainCoordinator = drainCoordinator;
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
        var lease = drainCoordinator.tryAcquire();
        if (lease == null) {
            return 0;
        }
        try (lease) {
            int total = 0;
            for (OwnerOutboxRelayPort relay : ownerRelays) {
                total += run(relay);
            }
            return total;
        }
    }

    private int run(OwnerOutboxRelayPort relay) {
        String owner = relay.owner();
        try {
            int published = Math.max(0, relay.dispatchPendingEvents());
            if (published > 0) {
                meterRegistry.counter("lumira.event.relay.published", "owner", owner).increment(published);
            }
            return published;
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ignored) {
            // An owner can be intentionally absent from a runtime assembly.
        } catch (RuntimeException exception) {
            meterRegistry.counter("lumira.event.relay.failure", "owner", owner).increment();
            log.warn("outbox relay failed owner={}: {}", owner, exception.getMessage());
        }
        return 0;
    }
}
