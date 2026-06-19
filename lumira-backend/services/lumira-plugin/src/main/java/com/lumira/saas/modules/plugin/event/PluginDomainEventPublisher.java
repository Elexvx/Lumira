package com.lumira.saas.modules.plugin.event;

import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service("pluginDomainEventPublisher")
public class PluginDomainEventPublisher implements DomainEventPublisher {

    private final PluginOutboxService outboxService;

    public PluginDomainEventPublisher(PluginOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        outboxService.recordAfterCommit(
                event.tenantId(),
                resolveUserId(event.attributes()),
                event.eventType(),
                event.eventKey(),
                envelope(event)
        );
    }

    private Long resolveUserId(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get("userId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private Map<String, Object> envelope(DomainEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId().toString());
        payload.put("eventType", event.eventType());
        payload.put("aggregateType", event.aggregateType());
        payload.put("aggregateId", event.aggregateId());
        payload.put("tenantId", event.tenantId());
        payload.put("schemaVersion", event.schemaVersion());
        payload.put("eventKey", event.eventKey());
        payload.put("occurredAt", event.occurredAt().toString());
        payload.put("attributes", event.attributes() == null ? Map.of() : event.attributes());
        return payload;
    }
}
