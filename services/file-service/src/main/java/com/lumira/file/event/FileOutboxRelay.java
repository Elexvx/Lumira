package com.lumira.file.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FileOutboxRelay {

    private final PlatformEventOutboxService outboxService;
    private final FileOutboxDispatcher dispatcher;
    private final boolean relayEnabled;
    private final int batchSize;

    public FileOutboxRelay(
            PlatformEventOutboxService outboxService,
            FileOutboxDispatcher dispatcher,
            @Value("${saas.event.outbox.relay-enabled:${SAAS_EVENT_OUTBOX_RELAY_ENABLED:false}}") boolean relayEnabled,
            @Value("${saas.event.outbox.batch-size:${SAAS_EVENT_OUTBOX_BATCH_SIZE:100}}") int batchSize
    ) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
        this.relayEnabled = relayEnabled;
        this.batchSize = batchSize;
    }

    public int dispatchPendingEvents() {
        if (!relayEnabled) {
            return 0;
        }
        return outboxService.dispatchPending(dispatcher, batchSize);
    }

    public boolean replay(Long id) {
        return outboxService.replay(id, dispatcher);
    }
}
