package com.lumira.saas.infrastructure.event;

import org.springframework.stereotype.Service;

@Service
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

    public void dispatchPendingEvents() {
        PlatformEventProperties.Outbox outbox = platformEventProperties.getOutbox();
        if (!outbox.isRelayEnabled()) {
            return;
        }

        platformEventOutboxService.dispatchPending(platformEventDispatcher, outbox.getBatchSize());
    }

    public boolean replay(Long eventId) {
        return platformEventOutboxService.replayById(eventId, platformEventDispatcher);
    }
}
