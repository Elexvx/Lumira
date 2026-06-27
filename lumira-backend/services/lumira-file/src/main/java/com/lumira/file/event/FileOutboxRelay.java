package com.lumira.file.event;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLumiraAsyncEnabled
public class FileOutboxRelay {

    private final PlatformEventOutboxService outboxService;
    private final FileOutboxDispatcher dispatcher;
    private final boolean relayEnabled;
    private final int batchSize;
    private final int maxDrainRounds;
    private final int maxBurstRounds;

    @Autowired
    public FileOutboxRelay(
            PlatformEventOutboxService outboxService,
            FileOutboxDispatcher dispatcher,
            @Value("${saas.event.outbox.relay-enabled:${SAAS_EVENT_OUTBOX_RELAY_ENABLED:false}}") boolean relayEnabled,
            @Value("${saas.event.outbox.batch-size:${SAAS_EVENT_OUTBOX_BATCH_SIZE:100}}") int batchSize,
            @Value("${saas.event.outbox.max-drain-rounds:${SAAS_EVENT_OUTBOX_MAX_DRAIN_ROUNDS:4}}") int maxDrainRounds,
            @Value("${saas.event.outbox.max-burst-rounds:${SAAS_EVENT_OUTBOX_MAX_BURST_ROUNDS:12}}") int maxBurstRounds
    ) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
        this.relayEnabled = relayEnabled;
        this.batchSize = batchSize;
        this.maxDrainRounds = maxDrainRounds;
        this.maxBurstRounds = maxBurstRounds;
    }

    FileOutboxRelay(
            PlatformEventOutboxService outboxService,
            FileOutboxDispatcher dispatcher,
            boolean relayEnabled,
            int batchSize,
            int maxDrainRounds
    ) {
        this(outboxService, dispatcher, relayEnabled, batchSize, maxDrainRounds, maxDrainRounds);
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
                outboxService.dispatchableBacklog()
        );
        int totalDelivered = 0;
        for (int round = 0; round < effectiveMaxRounds; round++) {
            int delivered = outboxService.dispatchPending(dispatcher, normalizedBatchSize);
            totalDelivered += delivered;
            if (delivered < normalizedBatchSize) {
                break;
            }
        }
        return totalDelivered;
    }

    public boolean replay(Long id) {
        return outboxService.replay(id, dispatcher);
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
