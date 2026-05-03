package com.legendary.invention.saas.infrastructure.event;

import org.springframework.scheduling.annotation.Scheduled;
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

    @Scheduled(fixedDelayString = "${saas.event.outbox.relay-fixed-delay-ms:${SAAS_EVENT_OUTBOX_RELAY_FIXED_DELAY_MS:5000}}")
    public void dispatchPendingEvents() {
        PlatformEventProperties.Outbox outbox = platformEventProperties.getOutbox();
        if (!outbox.isRelayEnabled()) {
            return;
        }

        platformEventOutboxService.dispatchPending(platformEventDispatcher, outbox.getBatchSize());
    }
}
