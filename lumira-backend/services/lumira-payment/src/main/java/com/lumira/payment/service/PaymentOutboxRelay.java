package com.lumira.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentOutboxRelay {

    private final PaymentOutboxService paymentOutboxService;
    private final PaymentOutboxDispatcher paymentOutboxDispatcher;
    private final boolean relayEnabled;
    private final int batchSize;

    public PaymentOutboxRelay(
            PaymentOutboxService paymentOutboxService,
            PaymentOutboxDispatcher paymentOutboxDispatcher,
            @Value("${saas.event.outbox.relay-enabled:${SAAS_EVENT_OUTBOX_RELAY_ENABLED:false}}") boolean relayEnabled,
            @Value("${saas.event.outbox.batch-size:${SAAS_EVENT_OUTBOX_BATCH_SIZE:100}}") int batchSize
    ) {
        this.paymentOutboxService = paymentOutboxService;
        this.paymentOutboxDispatcher = paymentOutboxDispatcher;
        this.relayEnabled = relayEnabled;
        this.batchSize = batchSize;
    }

    public int dispatchPendingEvents() {
        if (!relayEnabled) {
            return 0;
        }
        return paymentOutboxService.dispatchPending(paymentOutboxDispatcher, batchSize);
    }

    public boolean replay(Long id) {
        return paymentOutboxService.replay(id, paymentOutboxDispatcher);
    }
}
