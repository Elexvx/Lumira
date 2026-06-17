package com.lumira.file.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import java.util.Map;

public final class FileDomainModels {

    private FileDomainModels() {
    }

    public static final class FileObjectAggregate extends AggregateRoot<Long> {
        private final Long tenantId;
        private final long sizeBytes;
        private boolean deleted;

        public FileObjectAggregate(Long fileId, Long tenantId, long sizeBytes) {
            super(EntityId.of(fileId));
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes must not be negative");
            }
            this.tenantId = tenantId;
            this.sizeBytes = sizeBytes;
        }

        public void recordUploaded(String contentType) {
            registerEvent(StandardDomainEvent.of(
                    "FILE_OBJECT_UPLOADED",
                    "file.object",
                    String.valueOf(id().value()),
                    tenantId,
                    Map.of("sizeBytes", sizeBytes, "contentType", contentType == null ? "" : contentType)
            ));
        }

        public void delete() {
            if (deleted) {
                return;
            }
            deleted = true;
            registerEvent(StandardDomainEvent.of(
                    "FILE_OBJECT_DELETED",
                    "file.object",
                    String.valueOf(id().value()),
                    tenantId,
                    Map.of("sizeBytes", sizeBytes)
            ));
        }
    }

    public record StorageSpace(Long spaceId, Long tenantId, long quotaBytes, long usedBytes) {

        public boolean canAccept(long uploadBytes) {
            return uploadBytes >= 0 && usedBytes + uploadBytes <= quotaBytes;
        }
    }
}
