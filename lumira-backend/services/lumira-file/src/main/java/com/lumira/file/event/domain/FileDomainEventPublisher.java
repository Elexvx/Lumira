package com.lumira.file.event.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventPayloadDigests;
import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import com.lumira.common.web.TraceContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("fileDomainEventPublisher")
public class FileDomainEventPublisher implements DomainEventPublisher {

    private final PlatformEventOutboxService platformEventOutboxService;
    private final ObjectMapper objectMapper;
    private final String releaseId;

    public FileDomainEventPublisher(PlatformEventOutboxService platformEventOutboxService) {
        this(platformEventOutboxService, new ObjectMapper(), "unknown");
    }

    @Autowired
    public FileDomainEventPublisher(
            PlatformEventOutboxService platformEventOutboxService,
            ObjectMapper objectMapper,
            @Value("${lumira.release-id:${LUMIRA_RELEASE_ID:unknown}}") String releaseId
    ) {
        this(platformEventOutboxService, objectMapper, releaseId, true);
    }

    private FileDomainEventPublisher(
            PlatformEventOutboxService platformEventOutboxService,
            ObjectMapper objectMapper,
            String releaseId,
            boolean ignored
    ) {
        this.platformEventOutboxService = platformEventOutboxService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.releaseId = releaseId == null || releaseId.isBlank() ? "unknown" : releaseId.trim();
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
        Map<String, Object> attributes = event.attributes() == null
                ? Map.of()
                : new LinkedHashMap<>(event.attributes());
        Map<String, Object> payload = new LinkedHashMap<>();
        Instant occurredAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        payload.put("eventId", event.eventId().toString());
        payload.put("eventType", event.eventType());
        payload.put("sourceModule", "file");
        payload.put("producer", "file");
        payload.put("owner", "lumira-file");
        payload.put("schemaVersion", event.schemaVersion());
        payload.put("occurredAt", occurredAt.toString());
        payload.put("aggregateType", event.aggregateType());
        payload.put("aggregateId", event.aggregateId());
        payload.put("aggregateVersion", aggregateVersion(attributes));
        payload.put("eventKey", event.eventKey());
        payload.put("traceId", TraceContext.getTraceId());
        payload.put("releaseId", releaseId);
        payload.put("payload", attributes);
        // Keep the old top-level shape for existing file owner diagnostics while
        // the versioned integration contract uses payload as the canonical body.
        payload.put("attributes", attributes);
        payload.put("userId", resolveUserId(event.attributes()));
        String userUuid = resolveUserUuid(event.attributes());
        if (userUuid != null) {
            payload.put("userUuid", userUuid);
        }
        payload.put("payloadDigest", digest(attributes));
        return payload;
    }

    private Long aggregateVersion(Map<String, Object> attributes) {
        Object value = attributes.get("aggregateVersion");
        if (value instanceof Number number && number.longValue() > 0L) {
            return number.longValue();
        }
        throw new IllegalArgumentException("File event aggregateVersion is required");
    }

    private String digest(Map<String, Object> attributes) {
        try {
            return EventPayloadDigests.sha256(objectMapper.writeValueAsString(attributes));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("File event payload cannot be serialized", exception);
        }
    }

    private String resolveUserUuid(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get("userUuid");
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }
}
