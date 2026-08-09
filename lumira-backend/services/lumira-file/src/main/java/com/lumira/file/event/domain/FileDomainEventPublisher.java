package com.lumira.file.event.domain;

import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service("fileDomainEventPublisher")
public class FileDomainEventPublisher implements DomainEventPublisher {

    private final PlatformEventOutboxService platformEventOutboxService;

    public FileDomainEventPublisher(PlatformEventOutboxService platformEventOutboxService) {
        this.platformEventOutboxService = platformEventOutboxService;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        platformEventOutboxService.record(
                FilePlatformEventTypes.SOURCE_FILE,
                event.eventType(),
                resolveUserId(event.attributes()),
                event.eventKey(),
                payload(event)
        );
    }

    private Long resolveUserId(Map<String, Object> attributes) {
        if (resolveUserUuid(attributes) == null) {
            return null;
        }
        Object value = attributes == null ? null : attributes.get("userId");
        if (value instanceof Number number) {
            long userId = number.longValue();
            return userId > 0 ? userId : null;
        }
        return null;
    }

    private Map<String, Object> payload(DomainEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", event.schemaVersion());
        payload.put("occurredAt", event.occurredAt() == null ? Instant.now() : event.occurredAt());
        payload.put("userId", resolveUserId(event.attributes()));
        String userUuid = resolveUserUuid(event.attributes());
        if (userUuid != null) {
            payload.put("userUuid", userUuid);
        }
        payload.put("aggregateType", event.aggregateType());
        payload.put("aggregateId", event.aggregateId());
        payload.put("eventId", event.eventId().toString());
        payload.put("eventKey", event.eventKey());
        payload.put("attributes", event.attributes());
        return payload;
    }

    private String resolveUserUuid(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get("userUuid");
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }
}
