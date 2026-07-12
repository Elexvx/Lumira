package com.lumira.saas.modules.plugin.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "logging", matchIfMissing = true)
public class LoggingPluginOutboxDispatcher implements PluginOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPluginOutboxDispatcher.class);

    @Override
    public void dispatch(PluginOutboxRow row) {
        if (row == null) {
            return;
        }
        String payload = StringUtils.hasText(row.getPayloadJson()) ? row.getPayloadJson() : "{}";
        logger.info("plugin outbox dispatch: id={}, eventType={}, eventKey={}, payload={}",
                row.getId(), row.getEventType(), row.getEventKey(), payload);
    }
}
