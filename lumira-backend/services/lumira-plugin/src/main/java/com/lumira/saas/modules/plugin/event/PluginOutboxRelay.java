package com.lumira.saas.modules.plugin.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PluginOutboxRelay {

    private final PluginOutboxService pluginOutboxService;
    private final PluginOutboxDispatcher pluginOutboxDispatcher;
    private final boolean relayEnabled;
    private final int batchSize;

    public PluginOutboxRelay(
            PluginOutboxService pluginOutboxService,
            PluginOutboxDispatcher pluginOutboxDispatcher,
            @Value("${saas.event.outbox.relay-enabled:${SAAS_EVENT_OUTBOX_RELAY_ENABLED:false}}") boolean relayEnabled,
            @Value("${saas.event.outbox.batch-size:${SAAS_EVENT_OUTBOX_BATCH_SIZE:100}}") int batchSize
    ) {
        this.pluginOutboxService = pluginOutboxService;
        this.pluginOutboxDispatcher = pluginOutboxDispatcher;
        this.relayEnabled = relayEnabled;
        this.batchSize = batchSize;
    }

    public int dispatchPendingEvents() {
        if (!relayEnabled) {
            return 0;
        }
        return pluginOutboxService.dispatchPending(pluginOutboxDispatcher, batchSize);
    }

    public boolean replay(Long id) {
        return pluginOutboxService.replay(id, pluginOutboxDispatcher);
    }
}
