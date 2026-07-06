package com.lumira.saas.modules.plugin.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.ReadModel;
import java.util.LinkedHashMap;
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
            enable(version, null, null);
        }

        public void enable(String version, Long userId, String userUuid) {
            if (enabled) {
                return;
            }
            enabled = true;
            registerEvent(StandardDomainEvent.of(
                    "PLUGIN_ENABLED",
                    "plugin.activation",
                    id().value(),
                    actorAttributes("version", version == null ? "" : version, userId, userUuid)
            ));
        }

        public void disable(String reason) {
            disable(reason, null, null);
        }

        public void disable(String reason, Long userId, String userUuid) {
            if (!enabled) {
                return;
            }
            enabled = false;
            registerEvent(StandardDomainEvent.of(
                    "PLUGIN_DISABLED",
                    "plugin.activation",
                    id().value(),
                    actorAttributes("reason", reason == null ? "unspecified" : reason, userId, userUuid)
            ));
        }

        private Map<String, Object> actorAttributes(String key, Object value, Long userId, String userUuid) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put(key, value);
            if (userId != null) {
                if (userId <= 0 || userUuid == null || userUuid.isBlank()) {
                    throw new IllegalArgumentException("trusted actor identity is required");
                }
                attributes.put("userId", userId);
                attributes.put("userUuid", userUuid.trim());
            }
            return attributes;
        }
    }

    public record PluginManifestReadModel(
            String pluginCode,
            String version,
            Map<String, Object> manifest
    ) implements ReadModel {
    }
}
