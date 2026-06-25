package com.lumira.saas.modules.platform.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.VersionedReadModel;
import java.util.Map;

public final class PlatformDomainModels {

    private PlatformDomainModels() {
    }

    public static final class ConfigAggregate extends AggregateRoot<String> {
        private String value;

        public ConfigAggregate(String configKey, String value) {
            super(EntityId.of(configKey));
            this.value = value;
        }

        public void changeValue(String value) {
            if (String.valueOf(this.value).equals(String.valueOf(value))) {
                return;
            }
            this.value = value;
            registerEvent(StandardDomainEvent.of(
                    "PLATFORM_CONFIG_CHANGED",
                    "platform.config",
                    id().value(),
                    Map.of("configKey", id().value())
            ));
        }
    }

    public record RuntimeAppearanceReadModel(
            long version,
            Map<String, Object> settings
    ) implements VersionedReadModel {

        @Override
        public String cacheScope() {
            return "platform.runtime-appearance";
        }
    }
}
