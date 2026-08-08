package com.lumira.file.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "logging", matchIfMissing = true)
/** Default owner-side dispatcher; it must remain available when the separate async runtime is disabled. */
public class LoggingFileOutboxDispatcher implements FileOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFileOutboxDispatcher.class);

    @Override
    public void dispatch(PlatformEventOutboxEntity row) {
        logger.info(
                "file outbox event delivered id={} eventType={} eventKey={}",
                row == null ? null : row.getId(),
                row == null ? null : row.getEventType(),
                row == null ? null : row.getEventKey()
        );
    }
}
