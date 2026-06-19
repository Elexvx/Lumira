package com.lumira.file.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingFileOutboxDispatcher implements FileOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFileOutboxDispatcher.class);

    @Override
    public void dispatch(PlatformEventOutboxEntity row) {
        logger.info(
                "file outbox event delivered id={} tenantId={} eventType={} eventKey={}",
                row == null ? null : row.getId(),
                row == null ? null : row.getTenantId(),
                row == null ? null : row.getEventType(),
                row == null ? null : row.getEventKey()
        );
    }
}
