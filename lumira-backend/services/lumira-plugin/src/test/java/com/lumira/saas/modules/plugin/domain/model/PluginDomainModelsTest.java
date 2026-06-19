package com.lumira.saas.modules.plugin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.plugin.domain.model.PluginDomainModels.TenantPluginAggregate;
import org.junit.jupiter.api.Test;

class PluginDomainModelsTest {

    @Test
    void tenantPluginAggregateEmitsEnableAndDisableEventsOnce() {
        TenantPluginAggregate plugin = new TenantPluginAggregate("sensitive-words", 1L, false);

        plugin.enable("1.0.0");
        plugin.enable("1.0.0");
        plugin.disable("maintenance");
        plugin.disable("maintenance");

        assertThat(plugin.domainEvents()).hasSize(2);
        assertThat(plugin.domainEvents().get(0).eventType()).isEqualTo("PLUGIN_TENANT_ENABLED");
        assertThat(plugin.domainEvents().get(1).eventType()).isEqualTo("PLUGIN_TENANT_DISABLED");
    }
}
