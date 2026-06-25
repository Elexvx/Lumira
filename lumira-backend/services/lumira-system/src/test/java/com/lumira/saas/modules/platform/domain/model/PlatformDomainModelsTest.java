package com.lumira.saas.modules.platform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.domain.event.DomainEvent;
import com.lumira.saas.modules.platform.domain.model.PlatformDomainModels.ConfigAggregate;
import com.lumira.saas.modules.platform.domain.model.PlatformDomainModels.RuntimeAppearanceReadModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformDomainModelsTest {

    @Test
    void configAggregateEmitsEventOnlyWhenValueChanges() {
        ConfigAggregate config = new ConfigAggregate("branding.website-name", "Lumira");

        config.changeValue("Lumira");
        assertThat(config.domainEvents()).isEmpty();

        config.changeValue("Lumira Pro");

        assertThat(config.domainEvents()).hasSize(1);
        DomainEvent event = config.domainEvents().getFirst();
        assertThat(event.eventType()).isEqualTo("PLATFORM_CONFIG_CHANGED");
        assertThat(event.aggregateType()).isEqualTo("platform.config");
        assertThat(event.aggregateId()).isEqualTo("branding.website-name");
        assertThat(event.attributes()).containsEntry("configKey", "branding.website-name");
    }

    @Test
    void runtimeAppearanceReadModelUsesVersionScopeCacheKey() {
        RuntimeAppearanceReadModel readModel = new RuntimeAppearanceReadModel(
                5L,
                Map.of("websiteName", "Lumira", "watermarkEnabled", true)
        );

        assertThat(readModel.cacheScope()).isEqualTo("platform.runtime-appearance");
        assertThat(readModel.cacheKey()).isEqualTo("5:platform.runtime-appearance");
        assertThat(readModel.settings()).containsEntry("websiteName", "Lumira");
    }
}
