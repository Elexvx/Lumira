package com.lumira.saas.modules.plugin.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.ReadModel;
import java.util.Map;

public final class PluginDomainModels {

    private PluginDomainModels() {
    }

    public static final class PluginActivationAggregate extends AggregateRoot<String> {
        private boolean enabled;

        public PluginActivationAggregate(String pluginCode, boolean enabled) {
            super(EntityId.of(pluginCode));
            this.enabled = enabled;
        }

        public void enable(String version) {
            if (enabled) {
                return;
            }
            enabled = true;
            registerEvent(StandardDomainEvent.of(
                    "PLUGIN_ENABLED",
                    "plugin.activation",
                    id().value(),
                    Map.of("version", version == null ? "" : version)
            ));
        }

        public void disable(String reason) {
            if (!enabled) {
                return;
            }
            enabled = false;
            registerEvent(StandardDomainEvent.of(
                    "PLUGIN_DISABLED",
                    "plugin.activation",
                    id().value(),
                    Map.of("reason", reason == null ? "unspecified" : reason)
            ));
        }
    }

    public record PluginManifestReadModel(
            String pluginCode,
            String version,
            Map<String, Object> manifest
    ) implements ReadModel {
    }
}
