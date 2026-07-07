package com.lumira.job;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

@Component
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "saas.job.adaptive-relay", name = "enabled", havingValue = "true")
public class AdaptiveRelayScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveRelayScheduler.class);

    private final BackendJobClient backendJobClient;
    private final JobExecutorProperties.AdaptiveRelay properties;
    private final ScheduledExecutorService executor;
    private volatile boolean running;
    private volatile long idleDelayMs;

    @Autowired
    public AdaptiveRelayScheduler(BackendJobClient backendJobClient, JobExecutorProperties properties) {
        this(
                backendJobClient,
                properties.getAdaptiveRelay(),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "lumira-adaptive-relay");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    AdaptiveRelayScheduler(
            BackendJobClient backendJobClient,
            JobExecutorProperties.AdaptiveRelay properties,
            ScheduledExecutorService executor
    ) {
        this.backendJobClient = backendJobClient;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        idleDelayMs = normalizedInitialDelayMs();
        schedule(idleDelayMs);
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    void runOnce() {
        if (!running) {
            return;
        }
        long nextDelayMs = normalizedFailureDelayMs();
        try {
            RelayRunResult result = relayAll();
            nextDelayMs = nextDelayMs(result);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable throwable) {
            log.warn("adaptive relay scheduler failed before reschedule: {}", throwable.getMessage(), throwable);
        }
        schedule(nextDelayMs);
    }

    private RelayRunResult relayAll() {
        RelayRunResult result = new RelayRunResult();
        result.add(relay("platform", properties.isPlatformEnabled(), backendJobClient::relayOutbox));
        result.add(relay("system-export", properties.isPlatformEnabled(), backendJobClient::processExportTasks));
        result.add(relay("message", properties.isMessageEnabled(), backendJobClient::relayMessageOutbox));
        result.add(relay("file", properties.isFileEnabled(), backendJobClient::relayFileOutbox));
        result.add(relay("payment", properties.isPaymentEnabled(), backendJobClient::relayPaymentOutbox));
        result.add(relay("plugin", properties.isPluginEnabled(), backendJobClient::relayPluginOutbox));
        return result;
    }

    private RelayRunResult relay(String owner, boolean enabled, IntSupplier relay) {
        RelayRunResult result = new RelayRunResult();
        if (!enabled) {
            return result;
        }
        try {
            int delivered = Math.max(0, relay.getAsInt());
            result.delivered = delivered;
            if (delivered > 0) {
                log.debug("adaptive relay owner={} delivered={}", owner, delivered);
            }
        } catch (RuntimeException ex) {
            result.failed = true;
            log.warn("adaptive relay owner={} failed: {}", owner, ex.getMessage());
        }
        return result;
    }

    private long nextDelayMs(RelayRunResult result) {
        if (result.delivered > 0) {
            idleDelayMs = normalizedMinDelayMs();
            return idleDelayMs;
        }
        if (result.failed) {
            return normalizedFailureDelayMs();
        }
        long nextIdleDelayMs = Math.min(
                normalizedMaxDelayMs(),
                Math.max(normalizedMinDelayMs(), idleDelayMs * 2L)
        );
        idleDelayMs = nextIdleDelayMs;
        return nextIdleDelayMs;
    }

    private void schedule(long delayMs) {
        if (!running) {
            return;
        }
        executor.schedule(this::runOnce, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
    }

    private long normalizedInitialDelayMs() {
        return Math.max(normalizedMinDelayMs(), properties.getInitialDelayMs());
    }

    private long normalizedMinDelayMs() {
        return Math.max(100L, properties.getMinDelayMs());
    }

    private long normalizedMaxDelayMs() {
        return Math.max(normalizedMinDelayMs(), properties.getMaxDelayMs());
    }

    private long normalizedFailureDelayMs() {
        return Math.max(normalizedMinDelayMs(), properties.getFailureDelayMs());
    }

    static class RelayRunResult {
        private int delivered;
        private boolean failed;

        void add(RelayRunResult other) {
            delivered += other.delivered;
            failed = failed || other.failed;
        }
    }
}
