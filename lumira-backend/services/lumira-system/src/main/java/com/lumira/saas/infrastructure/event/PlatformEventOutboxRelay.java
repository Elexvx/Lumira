package com.lumira.saas.infrastructure.event;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLumiraAsyncEnabled
public class PlatformEventOutboxRelay {

    private final PlatformEventOutboxService platformEventOutboxService;
    private final PlatformEventDispatcher platformEventDispatcher;
    private final PlatformEventProperties platformEventProperties;

    public PlatformEventOutboxRelay(
            PlatformEventOutboxService platformEventOutboxService,
            PlatformEventDispatcher platformEventDispatcher,
            PlatformEventProperties platformEventProperties
    ) {
        this.platformEventOutboxService = platformEventOutboxService;
        this.platformEventDispatcher = platformEventDispatcher;
        this.platformEventProperties = platformEventProperties;
    }

    public int dispatchPendingEvents() {
        PlatformEventProperties.Outbox outbox = platformEventProperties.getOutbox();
        if (!outbox.isRelayEnabled()) {
            return 0;
        }

        int batchSize = Math.max(1, outbox.getBatchSize());
        int maxDrainRounds = Math.max(1, outbox.getMaxDrainRounds());
        int maxBurstRounds = Math.max(maxDrainRounds, outbox.getMaxBurstRounds());
        int effectiveMaxRounds = effectiveMaxRounds(
                batchSize,
                maxDrainRounds,
                maxBurstRounds,
                platformEventOutboxService.dispatchableBacklog()
        );
        int totalDelivered = 0;
        for (int round = 0; round < effectiveMaxRounds; round++) {
            int delivered = platformEventOutboxService.dispatchPending(platformEventDispatcher, batchSize);
            totalDelivered += delivered;
            if (delivered < batchSize) {
                break;
            }
        }
        return totalDelivered;
    }

    public boolean replay(Long eventId) {
        return platformEventOutboxService.replayById(eventId, platformEventDispatcher);
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
