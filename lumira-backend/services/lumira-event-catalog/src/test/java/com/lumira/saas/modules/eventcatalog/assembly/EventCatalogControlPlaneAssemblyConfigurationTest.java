package com.lumira.saas.modules.eventcatalog.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.eventcatalog.app.EventCatalogAppService;
import com.lumira.saas.modules.eventcatalog.controller.PublicEventCatalogController;
import com.lumira.saas.modules.eventcatalog.controller.internal.EventCatalogInternalJobController;
import com.lumira.saas.modules.eventcatalog.infrastructure.JdbcEventCatalogRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

class EventCatalogControlPlaneAssemblyConfigurationTest {

    @Test
    void explicitlyAssemblesOnlyCatalogOwnedComponents() {
        Import imported = EventCatalogControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(Arrays.asList(imported.value())).containsExactlyInAnyOrder(
                JdbcEventCatalogRepository.class,
                EventCatalogAppService.class,
                PublicEventCatalogController.class,
                EventCatalogInternalJobController.class
        );
    }
}
