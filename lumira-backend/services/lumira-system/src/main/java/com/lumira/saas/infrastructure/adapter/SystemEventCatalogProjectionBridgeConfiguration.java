package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.event.EventCatalogProjectionHandler;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.event.PlatformEventConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires a lazy bridge so System never imports the catalog implementation. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
public class SystemEventCatalogProjectionBridgeConfiguration {

    @Bean
    PlatformEventConsumer eventCatalogProjectionConsumer(ObjectProvider<EventCatalogProjectionHandler> projectionHandler) {
        return new SystemEventCatalogProjectionConsumerAdapter(projectionHandler);
    }
}
