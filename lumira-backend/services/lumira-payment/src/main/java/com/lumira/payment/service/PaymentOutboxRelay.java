package com.lumira.payment.service;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLumiraAsyncEnabled
public class PaymentOutboxRelay {

    private final PaymentOutboxService paymentOutboxService;
    private final PaymentOutboxDispatcher paymentOutboxDispatcher;
    private final boolean relayEnabled;
    private final int batchSize;
    private final int maxDrainRounds;
    private final int maxBurstRounds;

    @Autowired
    public PaymentOutboxRelay(
            PaymentOutboxService paymentOutboxService,
            PaymentOutboxDispatcher paymentOutboxDispatcher,
            @Value("${saas.event.outbox.relay-enabled:${SAAS_EVENT_OUTBOX_RELAY_ENABLED:false}}") boolean relayEnabled,
            @Value("${saas.event.outbox.batch-size:${SAAS_EVENT_OUTBOX_BATCH_SIZE:100}}") int batchSize,
            @Value("${saas.event.outbox.max-drain-rounds:${SAAS_EVENT_OUTBOX_MAX_DRAIN_ROUNDS:4}}") int maxDrainRounds,
            @Value("${saas.event.outbox.max-burst-rounds:${SAAS_EVENT_OUTBOX_MAX_BURST_ROUNDS:12}}") int maxBurstRounds
    ) {
        this.paymentOutboxService = paymentOutboxService;
        this.paymentOutboxDispatcher = paymentOutboxDispatcher;
        this.relayEnabled = relayEnabled;
        this.batchSize = batchSize;
        this.maxDrainRounds = maxDrainRounds;
        this.maxBurstRounds = maxBurstRounds;
    }

    PaymentOutboxRelay(
            PaymentOutboxService paymentOutboxService,
            PaymentOutboxDispatcher paymentOutboxDispatcher,
            boolean relayEnabled,
            int batchSize,
            int maxDrainRounds
    ) {
        this(paymentOutboxService, paymentOutboxDispatcher, relayEnabled, batchSize, maxDrainRounds, maxDrainRounds);
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
                paymentOutboxService.dispatchableBacklog()
        );
        int totalDelivered = 0;
        for (int round = 0; round < effectiveMaxRounds; round++) {
            int delivered = paymentOutboxService.dispatchPending(paymentOutboxDispatcher, normalizedBatchSize);
            totalDelivered += delivered;
            if (delivered < normalizedBatchSize) {
                break;
            }
        }
        return totalDelivered;
    }

    public boolean replay(Long id) {
        return paymentOutboxService.replay(id, paymentOutboxDispatcher);
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
