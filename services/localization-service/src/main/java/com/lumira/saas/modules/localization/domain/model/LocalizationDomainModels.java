package com.lumira.saas.modules.localization.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.VersionedReadModel;
import java.util.Map;

public final class LocalizationDomainModels {

    private LocalizationDomainModels() {
    }

    public static final class ReleaseAggregate extends AggregateRoot<Long> {
        private final Long tenantId;
        private final String namespace;
        private boolean published;

        public ReleaseAggregate(Long releaseId, Long tenantId, String namespace, boolean published) {
            super(EntityId.of(releaseId));
            this.tenantId = tenantId;
            this.namespace = namespace;
            this.published = published;
        }

        public void publish(long entryCount) {
            if (published) {
                return;
            }
            published = true;
            registerEvent(StandardDomainEvent.of(
                    "LOCALIZATION_RELEASE_PUBLISHED",
                    "localization.release",
                    String.valueOf(id().value()),
                    tenantId,
                    Map.of("namespace", namespace, "entryCount", entryCount)
            ));
        }

        public void rollbackTo(long targetVersion) {
            published = true;
            registerEvent(StandardDomainEvent.of(
                    "LOCALIZATION_RELEASE_ROLLED_BACK",
                    "localization.release",
                    String.valueOf(id().value()),
                    tenantId,
                    Map.of("namespace", namespace, "targetVersion", targetVersion)
            ));
        }
    }

    public record BundleReadModel(
            Long tenantId,
            String namespace,
            String locale,
            long version,
            Map<String, String> messages
    ) implements VersionedReadModel {

        @Override
        public String cacheScope() {
            return "localization.bundle:" + namespace + ":" + locale;
        }
    }
}
