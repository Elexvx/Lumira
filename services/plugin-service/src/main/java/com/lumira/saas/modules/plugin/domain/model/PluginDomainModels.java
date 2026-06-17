package com.lumira.saas.modules.plugin.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.ReadModel;
import java.util.Map;

public final class PluginDomainModels {

    private PluginDomainModels() {
    }

    public static final class TenantPluginAggregate extends AggregateRoot<String> {
        private final Long tenantId;
        private boolean enabled;

        public TenantPluginAggregate(String pluginCode, Long tenantId, boolean enabled) {
            super(EntityId.of(pluginCode));
            this.tenantId = tenantId;
            this.enabled = enabled;
        }

        public void enable(String version) {
            if (enabled) {
                return;
            }
            enabled = true;
            registerEvent(StandardDomainEvent.of(
                    "PLUGIN_TENANT_ENABLED",
                    "plugin.tenant-plugin",
                    id().value(),
                    tenantId,
                    Map.of("version", version == null ? "" : version)
            ));
        }

        public void disable(String reason) {
            if (!enabled) {
                return;
            }
            enabled = false;
            registerEvent(StandardDomainEvent.of(
                    "PLUGIN_TENANT_DISABLED",
                    "plugin.tenant-plugin",
                    id().value(),
                    tenantId,
                    Map.of("reason", reason == null ? "unspecified" : reason)
            ));
        }
    }

    public record PluginManifestReadModel(
            Long tenantId,
            String pluginCode,
            String version,
            Map<String, Object> manifest
    ) implements ReadModel {
    }
}
