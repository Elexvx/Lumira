package com.lumira.saas.modules.plugin.event;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class PluginOutboxRelay {

    private final PluginOutboxService pluginOutboxService;
    private final PluginOutboxDispatcher pluginOutboxDispatcher;
    private final boolean relayEnabled;
    private final int batchSize;
    private final int maxDrainRounds;
    private final int maxBurstRounds;

    @Autowired
    public PluginOutboxRelay(
            PluginOutboxService pluginOutboxService,
            PluginOutboxDispatcher pluginOutboxDispatcher,
            @Value("${saas.event.outbox.relay-enabled:${SAAS_EVENT_OUTBOX_RELAY_ENABLED:false}}") boolean relayEnabled,
            @Value("${saas.event.outbox.batch-size:${SAAS_EVENT_OUTBOX_BATCH_SIZE:100}}") int batchSize,
            @Value("${saas.event.outbox.max-drain-rounds:${SAAS_EVENT_OUTBOX_MAX_DRAIN_ROUNDS:4}}") int maxDrainRounds,
            @Value("${saas.event.outbox.max-burst-rounds:${SAAS_EVENT_OUTBOX_MAX_BURST_ROUNDS:12}}") int maxBurstRounds
    ) {
        this.pluginOutboxService = pluginOutboxService;
        this.pluginOutboxDispatcher = pluginOutboxDispatcher;
        this.relayEnabled = relayEnabled;
        this.batchSize = batchSize;
        this.maxDrainRounds = maxDrainRounds;
        this.maxBurstRounds = maxBurstRounds;
    }

    PluginOutboxRelay(
            PluginOutboxService pluginOutboxService,
            PluginOutboxDispatcher pluginOutboxDispatcher,
            boolean relayEnabled,
            int batchSize,
            int maxDrainRounds
    ) {
        this(pluginOutboxService, pluginOutboxDispatcher, relayEnabled, batchSize, maxDrainRounds, maxDrainRounds);
    }

    public int dispatchPendingEvents() {
        if (!relayEnabled) {
            return 0;
        }
        int normalizedBatchSize = Math.max(1, batchSize);
        int normalizedMaxDrainRounds = Math.max(1, maxDrainRounds);
        int normalizedMaxBurstRounds = Math.max(normalizedMaxDrainRounds, maxBurstRounds);
        int effectiveMaxRounds = effectiveMaxRounds(
                normalizedBatchSize,
                normalizedMaxDrainRounds,
                normalizedMaxBurstRounds,
                pluginOutboxService.dispatchableBacklog()
        );
        int totalDelivered = 0;
        for (int round = 0; round < effectiveMaxRounds; round++) {
            int delivered = pluginOutboxService.dispatchPending(pluginOutboxDispatcher, normalizedBatchSize);
            totalDelivered += delivered;
            if (delivered < normalizedBatchSize) {
                break;
            }
        }
        return totalDelivered;
    }

    public boolean replay(Long id) {
        return pluginOutboxService.replay(id, pluginOutboxDispatcher);
    }

    private int effectiveMaxRounds(
            int normalizedBatchSize,
            int normalizedMaxDrainRounds,
            int normalizedMaxBurstRounds,
            long dispatchableBacklog
    ) {
        if (dispatchableBacklog <= 0L) {
            return normalizedMaxDrainRounds;
        }
        long requiredRounds = Math.max(1L, (dispatchableBacklog + normalizedBatchSize - 1L) / normalizedBatchSize);
        return (int) Math.min(
                normalizedMaxBurstRounds,
                Math.max((long) normalizedMaxDrainRounds, requiredRounds)
        );
    }
}
