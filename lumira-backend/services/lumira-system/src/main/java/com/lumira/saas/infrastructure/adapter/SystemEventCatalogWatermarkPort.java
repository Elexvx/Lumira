package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.event.EventCatalogEventWatermarkPort;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxService;

/** Narrow System adapter; catalog rebuilds never query the platform outbox table directly. */
public class SystemEventCatalogWatermarkPort implements EventCatalogEventWatermarkPort {

    private final PlatformEventOutboxService platformEventOutboxService;

    public SystemEventCatalogWatermarkPort(PlatformEventOutboxService platformEventOutboxService) {
        this.platformEventOutboxService = platformEventOutboxService;
    }

    @Override
    public long currentWatermark() {
        return platformEventOutboxService.currentWatermark();
    }
}
