package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.event.PlatformEventPort;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import java.util.Map;

/** System-owned platform event publisher exposed through a context-neutral port. */
public class SystemPlatformEventPort implements PlatformEventPort {

    private final PlatformEventPublisher platformEventPublisher;

    public SystemPlatformEventPort(PlatformEventPublisher platformEventPublisher) {
        this.platformEventPublisher = platformEventPublisher;
    }

    @Override
    public void record(
            String sourceType,
            String eventType,
            Long userId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> attributes
    ) {
        platformEventPublisher.record(
                sourceType,
                eventType,
                userId,
                aggregateType,
                aggregateId,
                attributes
        );
    }
}
