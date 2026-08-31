package com.lumira.asyncruntime;

import com.lumira.api.event.OwnerOutboxRelayPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

final class OwnerRelayLane implements AutoCloseable {
    private final OwnerOutboxRelayPort relay;
    private final String owner;
    private final OwnerRelayLaneProperties.LaneSettings settings;
    private final MeterRegistry meters;
    private final AsyncRuntimeDrainCoordinator drain;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilNanos = new AtomicLong();

    OwnerRelayLane(
            OwnerOutboxRelayPort relay,
            OwnerRelayLaneProperties.LaneSettings settings,
            MeterRegistry meters,
            AsyncRuntimeDrainCoordinator drain
    ) {
        this.relay = relay;
        this.owner = relay.owner();
        this.settings = settings;
        this.meters = meters;
        this.drain = drain;
        this.executor = new ThreadPoolExecutor(
                settings.maxConcurrency(),
                settings.maxConcurrency(),
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "outbox-relay-" + owner);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        Gauge.builder("lumira.event.relay.lane.active", executor, ThreadPoolExecutor::getActiveCount)
                .tag("owner", owner)
                .register(meters);
        Gauge.builder("lumira.event.relay.lane.queued", executor, value -> value.getQueue().size())
                .tag("owner", owner)
                .register(meters);
    }

    String owner() {
        return owner;
    }

    CompletableFuture<Integer> dispatch() {
        return submit("relay", relay::dispatchPendingEvents);
    }

    CompletableFuture<Integer> replay(long eventId) {
        return submit("replay", () -> relay.replay(eventId) ? 1 : 0);
    }

    private CompletableFuture<Integer> submit(String operation, IntSupplier task) {
        if (isCircuitOpen()) {
            meters.counter("lumira.event.relay.circuit.rejected", "owner", owner, "operation", operation).increment();
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> result = new CompletableFuture<>();
        try {
            executor.execute(() -> execute(operation, task, result));
            meters.counter("lumira.event.relay.lane.submitted", "owner", owner, "operation", operation).increment();
        } catch (RejectedExecutionException exception) {
            meters.counter("lumira.event.relay.lane.rejected", "owner", owner, "operation", operation).increment();
            result.complete(0);
        }
        return result;
    }

    private void execute(String operation, IntSupplier task, CompletableFuture<Integer> result) {
        var lease = drain.tryAcquire();
        if (lease == null) {
            meters.counter("lumira.event.relay.lane.rejected", "owner", owner, "operation", "draining").increment();
            result.complete(0);
            return;
        }
        Timer.Sample sample = Timer.start(meters);
        try (lease) {
            RuntimeException lastFailure = null;
            for (int attempt = 0; attempt <= settings.retryBudget(); attempt++) {
                try {
                    int published = Math.max(0, task.getAsInt());
                    consecutiveFailures.set(0);
                    if (published > 0) {
                        meters.counter("lumira.event.relay.published", "owner", owner, "operation", operation)
                                .increment(published);
                    }
                    meters.counter("lumira.event.relay.success", "owner", owner, "operation", operation).increment();
                    result.complete(published);
                    return;
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                    if (attempt < settings.retryBudget()) {
                        meters.counter("lumira.event.relay.retry", "owner", owner, "operation", operation).increment();
                        if (!backoff(attempt + 1)) break;
                    }
                }
            }
            recordFailure(operation);
            result.completeExceptionally(lastFailure == null
                    ? new IllegalStateException("owner relay failed")
                    : lastFailure);
        } finally {
            sample.stop(Timer.builder("lumira.event.relay.duration")
                    .tag("owner", owner)
                    .tag("operation", operation)
                    .register(meters));
        }
    }

    private boolean backoff(int retryNumber) {
        try {
            Thread.sleep(Math.max(1L, settings.retryBackoff().toMillis() * retryNumber));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void recordFailure(String operation) {
        meters.counter("lumira.event.relay.failure", "owner", owner, "operation", operation).increment();
        if (consecutiveFailures.incrementAndGet() >= settings.circuitFailureThreshold()) {
            circuitOpenUntilNanos.set(System.nanoTime() + settings.circuitOpenDuration().toNanos());
            consecutiveFailures.set(0);
            meters.counter("lumira.event.relay.circuit.opened", "owner", owner).increment();
        }
    }

    private boolean isCircuitOpen() {
        long openUntil = circuitOpenUntilNanos.get();
        if (openUntil == 0L) return false;
        if (System.nanoTime() < openUntil) return true;
        return !circuitOpenUntilNanos.compareAndSet(openUntil, 0L);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
