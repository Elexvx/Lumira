package com.lumira.saas.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "logging", matchIfMissing = true)
public class LoggingPlatformEventDispatcher implements PlatformEventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPlatformEventDispatcher.class);

    @Override
    public void dispatch(PlatformEventOutboxEntity event) {
        logger.info(
                "平台事件已进入默认投递器: id={}, sourceType={}, eventType={}, eventKey={}",
                event.getId(),
                event.getSourceType(),
                event.getEventType(),
                event.getEventKey()
        );
    }
}
