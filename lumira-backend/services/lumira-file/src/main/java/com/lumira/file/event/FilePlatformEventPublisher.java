package com.lumira.file.event;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FilePlatformEventPublisher {

    private static final int SCHEMA_VERSION = 1;
    private static final String STATUS_ENABLED = "ENABLED";

    private final PlatformEventOutboxService platformEventOutboxService;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;

    public FilePlatformEventPublisher(PlatformEventOutboxService platformEventOutboxService) {
        this(platformEventOutboxService, null);
    }

    @Autowired
    public FilePlatformEventPublisher(
            PlatformEventOutboxService platformEventOutboxService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this.platformEventOutboxService = platformEventOutboxService;
        this.systemInternalApiProvider = systemInternalApiProvider;
    }

    public void publishUploadedAfterCommit(CurrentUser currentUser, FileObjectDTO file) {
        publishAfterCommit(FilePlatformEventTypes.FILE_OBJECT_UPLOADED, currentUser, file);
    }

    public void publishDeletedAfterCommit(CurrentUser currentUser, FileObjectDTO file) {
        publishAfterCommit(FilePlatformEventTypes.FILE_OBJECT_DELETED, currentUser, file);
    }

    String buildEventKey(String eventType, Long fileId) {
        return eventType + ":" + FilePlatformEventTypes.AGGREGATE_FILE_OBJECT + ":" + (fileId == null ? "none" : fileId);
    }

    private void publishAfterCommit(String eventType, CurrentUser currentUser, FileObjectDTO file) {
        TrustedActor actor = trustedActor(currentUser);
        Long userId = actor.userId();
        String userUuid = actor.userUuid();
        Long fileId = file == null ? null : file.id();
        platformEventOutboxService.recordAfterCommit(
                FilePlatformEventTypes.SOURCE_FILE,
                eventType,
                userId,
                buildEventKey(eventType, fileId),
                buildPayload(userId, userUuid, file)
        );
    }

    private Map<String, Object> buildPayload(Long userId, String userUuid, FileObjectDTO file) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("occurredAt", LocalDateTime.now());
        payload.put("userId", userId);
        payload.put("userUuid", userUuid);
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

    private void requireCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private TrustedActor trustedActor(CurrentUser currentUser) {
        requireCurrentUser(currentUser);
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApiProvider == null) {
            return new TrustedActor(userId, userUuid);
        }
        SystemInternalApi internalApi = systemInternalApiProvider.getIfAvailable();
        if (internalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user resolver is unavailable");
        }
        SystemUserSnapshotDTO snapshot = internalApi.findUserIdentityById(userId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user does not exist");
        }
        if (!StringUtils.hasText(snapshot.userUuid()) || !snapshot.userUuid().trim().equals(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user identity mismatch");
        }
        if (!StringUtils.hasText(snapshot.status()) || !STATUS_ENABLED.equalsIgnoreCase(snapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user is disabled");
        }
        return new TrustedActor(snapshot.userId(), snapshot.userUuid().trim());
    }

    private record TrustedActor(Long userId, String userUuid) {
    }
}
