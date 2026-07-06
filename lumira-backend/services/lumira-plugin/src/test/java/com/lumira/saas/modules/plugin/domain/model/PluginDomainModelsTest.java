package com.lumira.saas.modules.plugin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void pluginActivationAggregateShouldCarryTrustedActorWhenPresent() {
        PluginActivationAggregate plugin = new PluginActivationAggregate("sensitive-words", false);

        plugin.enable("1.0.0", 1001L, " user-uuid-1001 ");

        assertThat(plugin.domainEvents()).hasSize(1);
        assertThat(plugin.domainEvents().getFirst().attributes())
                .containsEntry("version", "1.0.0")
                .containsEntry("userId", 1001L)
                .containsEntry("userUuid", "user-uuid-1001");
    }

    @Test
    void pluginActivationAggregateShouldRejectActorUserIdWithoutUserUuid() {
        PluginActivationAggregate plugin = new PluginActivationAggregate("sensitive-words", false);

        assertThatThrownBy(() -> plugin.enable("1.0.0", 1001L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(plugin.domainEvents()).isEmpty();
    }
}
