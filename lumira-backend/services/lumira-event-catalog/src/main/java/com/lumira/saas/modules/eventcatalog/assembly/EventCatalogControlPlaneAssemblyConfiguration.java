package com.lumira.saas.modules.eventcatalog.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.eventcatalog.app.EventCatalogAppService;
import com.lumira.saas.modules.eventcatalog.controller.PublicEventCatalogController;
import com.lumira.saas.modules.eventcatalog.controller.internal.EventCatalogInternalJobController;
import com.lumira.saas.modules.eventcatalog.infrastructure.JdbcEventCatalogRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit server-only assembly for the event catalog read-model owner. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@Import({
        JdbcEventCatalogRepository.class,
        EventCatalogAppService.class,
        PublicEventCatalogController.class,
        EventCatalogInternalJobController.class
})
public class EventCatalogControlPlaneAssemblyConfiguration {
}
