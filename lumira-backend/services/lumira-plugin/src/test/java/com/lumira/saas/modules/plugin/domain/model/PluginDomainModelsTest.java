package com.lumira.saas.modules.plugin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.plugin.domain.model.PluginDomainModels.PluginActivationAggregate;
import org.junit.jupiter.api.Test;

class PluginDomainModelsTest {

    @Test
    void pluginActivationAggregateEmitsEnableAndDisableEventsOnce() {
        PluginActivationAggregate plugin = new PluginActivationAggregate("sensitive-words", false);

        plugin.enable("1.0.0");
        plugin.enable("1.0.0");
        plugin.disable("maintenance");
        plugin.disable("maintenance");

        assertThat(plugin.domainEvents()).hasSize(2);
        assertThat(plugin.domainEvents().get(0).eventType()).isEqualTo("PLUGIN_ENABLED");
        assertThat(plugin.domainEvents().get(1).eventType()).isEqualTo("PLUGIN_DISABLED");
    }
}
