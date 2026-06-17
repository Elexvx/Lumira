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
        platformEventOutboxService.recordAfterCommit(
                FilePlatformEventTypes.SOURCE_FILE,
                event.eventType(),
                event.tenantId(),
                null,
                event.eventKey(),
                payload(event)
        );
    }

    private Map<String, Object> payload(DomainEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", event.schemaVersion());
        payload.put("occurredAt", event.occurredAt() == null ? Instant.now() : event.occurredAt());
        payload.put("tenantId", event.tenantId());
        payload.put("userId", null);
        payload.put("aggregateType", event.aggregateType());
        payload.put("aggregateId", event.aggregateId());
        payload.put("eventId", event.eventId().toString());
        payload.put("eventKey", event.eventKey());
        payload.put("attributes", event.attributes());
        return payload;
    }
}
