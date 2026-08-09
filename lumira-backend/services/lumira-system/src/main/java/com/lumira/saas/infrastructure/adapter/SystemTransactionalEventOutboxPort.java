package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.event.TransactionalEventOutboxPort;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import java.util.Map;

/** Exposes System's durable outbox through a transaction-explicit shared port. */
public class SystemTransactionalEventOutboxPort implements TransactionalEventOutboxPort {

    private final PlatformEventPublisher platformEventPublisher;

    public SystemTransactionalEventOutboxPort(PlatformEventPublisher platformEventPublisher) {
        this.platformEventPublisher = platformEventPublisher;
    }

    @Override
    public void record(
            String eventType,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    ) {
        platformEventPublisher.recordInCurrentTransaction(
                eventType,
                userId,
                aggregateType,
                aggregateId,
                attributes
        );
    }
}
