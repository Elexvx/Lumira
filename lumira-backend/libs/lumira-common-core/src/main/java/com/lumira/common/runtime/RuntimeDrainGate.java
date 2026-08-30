package com.lumira.common.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local admission and in-flight tracker used by async and job runtimes.
 * A lease is acquired before business work starts and must be closed after the
 * durable ACK or handler completion boundary.
 */
public final class RuntimeDrainGate {
    private final AtomicBoolean acceptingNewWork = new AtomicBoolean(true);
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Instant> activeLeases = new ConcurrentHashMap<>();
    private final Clock clock;

    public RuntimeDrainGate() {
        this(Clock.systemUTC());
    }

    RuntimeDrainGate(Clock clock) {
        this.clock = clock;
    }

    public Lease tryAcquire() {
        if (!acceptingNewWork.get()) {
            return null;
        }
        long id = sequence.incrementAndGet();
        activeLeases.put(id, clock.instant());
        if (!acceptingNewWork.get()) {
            activeLeases.remove(id);
            return null;
        }
        return new Lease(id);
    }

    public void quiesce() {
        acceptingNewWork.set(false);
    }

    public void resume() {
        acceptingNewWork.set(true);
    }

    public Snapshot snapshot() {
        Instant now = clock.instant();
        long oldestAge = activeLeases.values().stream()
                .mapToLong(startedAt -> Math.max(0L, Duration.between(startedAt, now).toSeconds()))
                .max()
                .orElse(0L);
        int inflight = activeLeases.size();
        boolean accepting = acceptingNewWork.get();
        return new Snapshot(accepting, inflight, oldestAge, !accepting && inflight == 0);
    }

    public record Snapshot(
            boolean acceptingNewWork,
            int inflightTasks,
            long oldestInflightAgeSeconds,
            boolean safeToStop
    ) {
    }

    public final class Lease implements AutoCloseable {
        private final long id;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(long id) {
            this.id = id;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                activeLeases.remove(id);
            }
        }
    }
}
