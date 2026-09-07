package com.lumira.file.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FileDomainModels {

    private FileDomainModels() {
    }

    public static final class FileObjectAggregate extends AggregateRoot<Long> {
        private final long sizeBytes;
        private boolean deleted;

        public FileObjectAggregate(Long fileId, long sizeBytes) {
            super(EntityId.of(fileId));
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes must not be negative");
            }
            this.sizeBytes = sizeBytes;
        }

        public void recordUploaded(String contentType) {
            recordUploaded(contentType, null, null);
        }

        public void recordUploaded(String contentType, Long userId, String userUuid) {
            registerEvent(StandardDomainEvent.of(
                    "FILE_OBJECT_UPLOADED",
                    "file.object",
                    String.valueOf(id().value()),
                    actorAttributes(
                            Map.of(
                                    "aggregateVersion", 1L,
                                    "sizeBytes", sizeBytes,
                                    "contentType", contentType == null ? "" : contentType
                            ),
                            userId,
                            userUuid
                    )
            ));
        }

        public void delete() {
            delete(null, null);
        }

        public void delete(Long userId, String userUuid) {
            if (deleted) {
                return;
            }
            deleted = true;
            registerEvent(StandardDomainEvent.of(
                    "FILE_OBJECT_DELETED",
                    "file.object",
                    String.valueOf(id().value()),
                    actorAttributes(Map.of("aggregateVersion", 2L, "sizeBytes", sizeBytes), userId, userUuid)
            ));
        }

        private Map<String, Object> actorAttributes(Map<String, Object> baseAttributes, Long userId, String userUuid) {
            Map<String, Object> attributes = new LinkedHashMap<>(baseAttributes);
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

    public record StorageSpace(Long spaceId, long quotaBytes, long usedBytes) {

        public boolean canAccept(long uploadBytes) {
            return uploadBytes >= 0 && usedBytes + uploadBytes <= quotaBytes;
        }
    }
}
