package com.lumira.saas.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "logging", matchIfMissing = true)
public class LoggingPlatformEventDispatcher implements PlatformEventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPlatformEventDispatcher.class);
    private final List<PlatformEventConsumer> consumers;

    public LoggingPlatformEventDispatcher(List<PlatformEventConsumer> consumers) {
        this.consumers = consumers == null ? List.of() : consumers;
    }

    @Override
    public void dispatch(PlatformEventOutboxEntity event) {
        PlatformEventTrustValidator.requireTrustedSystemEvent(event);
        logger.info(
                "Platform event dispatched: id={}, sourceType={}, eventType={}, eventKey={}",
                event.getId(),
                event.getSourceType(),
                event.getEventType(),
                event.getEventKey()
        );
        for (PlatformEventConsumer consumer : consumers) {
            if (consumer.supports(event)) {
                consumer.consume(event);
            }
        }
    }
}
