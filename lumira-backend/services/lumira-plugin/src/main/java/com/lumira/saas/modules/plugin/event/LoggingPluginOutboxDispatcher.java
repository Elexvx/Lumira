package com.lumira.saas.modules.plugin.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LoggingPluginOutboxDispatcher implements PluginOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPluginOutboxDispatcher.class);

    @Override
    public void dispatch(PluginOutboxRow row) {
        if (row == null) {
            return;
        }
        String payload = StringUtils.hasText(row.getPayloadJson()) ? row.getPayloadJson() : "{}";
        logger.info("plugin outbox dispatch: id={}, tenantId={}, eventType={}, eventKey={}, payload={}",
                row.getId(), row.getTenantId(), row.getEventType(), row.getEventKey(), payload);
    }
}
