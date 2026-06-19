package com.lumira.file.event;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FilePlatformEventPublisher {

    private static final int SCHEMA_VERSION = 1;

    private final PlatformEventOutboxService platformEventOutboxService;

    public FilePlatformEventPublisher(PlatformEventOutboxService platformEventOutboxService) {
        this.platformEventOutboxService = platformEventOutboxService;
    }

    public void publishUploadedAfterCommit(CurrentUser currentUser, FileObjectDTO file) {
        publishAfterCommit(FilePlatformEventTypes.FILE_OBJECT_UPLOADED, currentUser, file);
    }

    public void publishDeletedAfterCommit(CurrentUser currentUser, FileObjectDTO file) {
        publishAfterCommit(FilePlatformEventTypes.FILE_OBJECT_DELETED, currentUser, file);
    }

    String buildEventKey(String eventType, Long tenantId, Long fileId) {
        return eventType + ":" + (tenantId == null ? "unknown" : tenantId) + ":" + FilePlatformEventTypes.AGGREGATE_FILE_OBJECT + ":" + (fileId == null ? "none" : fileId);
    }

    private void publishAfterCommit(String eventType, CurrentUser currentUser, FileObjectDTO file) {
        Long tenantId = file == null ? currentTenantId(currentUser) : file.tenantId();
        Long userId = currentUser == null ? null : currentUser.getUserId();
        Long fileId = file == null ? null : file.id();
        platformEventOutboxService.recordAfterCommit(
                FilePlatformEventTypes.SOURCE_FILE,
                eventType,
                tenantId,
                userId,
                buildEventKey(eventType, tenantId, fileId),
                buildPayload(tenantId, userId, file)
        );
    }

    private Map<String, Object> buildPayload(Long tenantId, Long userId, FileObjectDTO file) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("occurredAt", LocalDateTime.now());
        payload.put("tenantId", tenantId);
        payload.put("userId", userId);
        payload.put("aggregateType", FilePlatformEventTypes.AGGREGATE_FILE_OBJECT);
        payload.put("aggregateId", file == null ? null : file.id());
        payload.put("attributes", buildAttributes(file));
        return payload;
    }

    private Map<String, Object> buildAttributes(FileObjectDTO file) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (file == null) {
            return attributes;
        }
        attributes.put("fileId", file.id());
        attributes.put("originalFileName", file.originalFileName());
        attributes.put("bucket", file.bucket());
        attributes.put("storageType", file.storageType());
        attributes.put("storagePath", file.storagePath());
        attributes.put("mimeType", file.mimeType());
        attributes.put("fileExtension", file.fileExtension());
        attributes.put("fileSizeBytes", file.fileSizeBytes());
        attributes.put("category", file.category());
        attributes.put("uploadedBy", file.uploadedBy());
        return attributes;
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser != null && currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        return com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }
}
