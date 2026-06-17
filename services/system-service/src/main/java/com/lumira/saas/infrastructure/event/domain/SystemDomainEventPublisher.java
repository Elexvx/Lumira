package com.lumira.saas.infrastructure.event.domain;

import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service("systemDomainEventPublisher")
public class SystemDomainEventPublisher implements DomainEventPublisher {

    private final PlatformEventPublisher platformEventPublisher;

    public SystemDomainEventPublisher(PlatformEventPublisher platformEventPublisher) {
        this.platformEventPublisher = platformEventPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        platformEventPublisher.publishAfterCommit(
                resolveSourceType(event),
                event.eventType(),
                event.tenantId(),
                null,
                event.aggregateType(),
                parseLong(event.aggregateId()),
                attributes(event)
        );
    }

    private String resolveSourceType(DomainEvent event) {
        String type = event.eventType();
        if (type != null && type.startsWith("AI_")) {
            return PlatformEventTypes.SOURCE_AI;
        }
        if (type != null && type.startsWith("IAM_")) {
            return "IAM";
        }
        if (type != null && type.startsWith("PLATFORM_")) {
            return PlatformEventTypes.SOURCE_SYSTEM;
        }
        return PlatformEventTypes.SOURCE_SYSTEM;
    }

    private Map<String, Object> attributes(DomainEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>(event.attributes());
        attributes.put("eventId", event.eventId().toString());
        attributes.put("eventKey", event.eventKey());
        attributes.put("schemaVersion", event.schemaVersion());
        return attributes;
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
