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
        String eventType = resolveEventType(event.eventType());
        platformEventPublisher.record(
                resolveSourceType(event),
                eventType,
                resolveUserId(event.attributes()),
                event.aggregateType(),
                parseLong(event.aggregateId()),
                attributes(event, eventType)
        );
    }

    private Long resolveUserId(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get("userId");
        if (value instanceof Number number) {
            long userId = number.longValue();
            return userId > 0 ? userId : null;
        }
        return null;
    }

    private String resolveSourceType(DomainEvent event) {
        String type = event.eventType();
        if (type != null && type.startsWith("AI_")) {
            return PlatformEventTypes.SOURCE_AI;
        }
        if (type != null && type.startsWith("IAM_")) {
            // PlatformEventOutboxService is the SYSTEM owner outbox. IAM is
            // the producer identity carried in the payload, not a second
            // outbox source plane.
            return PlatformEventTypes.SOURCE_SYSTEM;
        }
        if (type != null && type.startsWith("PLATFORM_")) {
            return PlatformEventTypes.SOURCE_SYSTEM;
        }
        return PlatformEventTypes.SOURCE_SYSTEM;
    }

    private String resolveEventType(String domainEventType) {
        if ("IAM_ROLE_CHANGED".equals(domainEventType)) {
            return PlatformEventTypes.IAM_ROLE_CHANGED;
        }
        if ("IAM_ROLE_PERMISSIONS_CHANGED".equals(domainEventType)) {
            return PlatformEventTypes.IAM_PERMISSION_POLICY_CHANGED;
        }
        return domainEventType;
    }

    private Map<String, Object> attributes(DomainEvent event, String eventType) {
        Map<String, Object> attributes = new LinkedHashMap<>(event.attributes() == null ? Map.of() : event.attributes());
        attributes.put("eventId", event.eventId().toString());
        attributes.put("eventKey", event.eventKey());
        attributes.put("schemaVersion", event.schemaVersion());
        if (PlatformEventTypes.IAM_ROLE_CHANGED.equals(eventType)
                || PlatformEventTypes.IAM_PERMISSION_POLICY_CHANGED.equals(eventType)) {
            attributes.put("sourceModule", "iam");
            attributes.put("producer", PlatformEventTypes.IAM_PRODUCER);
            attributes.put("owner", PlatformEventTypes.IAM_OWNER);
            if (PlatformEventTypes.IAM_ROLE_CHANGED.equals(eventType)) {
                attributes.putIfAbsent("roleId", event.aggregateId());
            } else {
                attributes.putIfAbsent("policyScope", "ROLE");
                attributes.putIfAbsent("policyId", event.aggregateId());
                attributes.putIfAbsent("changeType", "UPDATED");
            }
        }
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
