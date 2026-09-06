package com.lumira.file.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.event.EventPayloadDigests;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.web.TraceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FilePlatformEventPublisher {

    private static final int SCHEMA_VERSION = 1;
    private static final String STATUS_ENABLED = "ENABLED";

    private final PlatformEventOutboxService platformEventOutboxService;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;
    private final ObjectMapper objectMapper;
    private final String releaseId;

    public FilePlatformEventPublisher(PlatformEventOutboxService platformEventOutboxService) {
        this(platformEventOutboxService, null, new ObjectMapper(), "unknown");
    }

    public FilePlatformEventPublisher(
            PlatformEventOutboxService platformEventOutboxService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this(platformEventOutboxService, systemInternalApiProvider, new ObjectMapper(), "unknown");
    }

    @Autowired
    public FilePlatformEventPublisher(
            PlatformEventOutboxService platformEventOutboxService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            ObjectMapper objectMapper,
            @Value("${lumira.release-id:${LUMIRA_RELEASE_ID:unknown}}") String releaseId
    ) {
        this.platformEventOutboxService = platformEventOutboxService;
        this.systemInternalApiProvider = systemInternalApiProvider;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.releaseId = releaseId == null || releaseId.isBlank() ? "unknown" : releaseId.trim();
    }

    public void publishUploaded(CurrentUser currentUser, FileObjectDTO file) {
        publish(FilePlatformEventTypes.FILE_OBJECT_UPLOADED, currentUser, file);
    }

    public void publishDeleted(CurrentUser currentUser, FileObjectDTO file) {
        publish(FilePlatformEventTypes.FILE_OBJECT_DELETED, currentUser, file);
    }

    String buildEventKey(String eventType, Long fileId) {
        return eventType + ":" + FilePlatformEventTypes.AGGREGATE_FILE_OBJECT + ":" + (fileId == null ? "none" : fileId);
    }

    private void publish(String eventType, CurrentUser currentUser, FileObjectDTO file) {
        TrustedActor actor = trustedActor(currentUser);
        Long userId = actor.userId();
        String userUuid = actor.userUuid();
        Long simulatedRoleId = actor.simulatedRoleId();
        Long fileId = file == null ? null : file.id();
        platformEventOutboxService.record(
                FilePlatformEventTypes.SOURCE_FILE,
                eventType,
                userId,
                buildEventKey(eventType, fileId),
                buildPayload(eventType, userId, userUuid, simulatedRoleId, file)
        );
    }

    private Map<String, Object> buildPayload(
            String eventType,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            FileObjectDTO file
    ) {
        Map<String, Object> attributes = buildAttributes(file);
        attributes.put("aggregateVersion", aggregateVersion(eventType));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("sourceModule", FilePlatformEventTypes.SOURCE_FILE);
        payload.put("producer", FilePlatformEventTypes.SOURCE_FILE);
        payload.put("owner", "lumira-file");
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("releaseId", releaseId);
        payload.put("traceId", TraceContext.getTraceId());
        payload.put("userId", userId);
        payload.put("userUuid", userUuid);
        if (simulatedRoleId != null) {
            payload.put("simulatedRoleId", simulatedRoleId);
        }
        payload.put("aggregateType", FilePlatformEventTypes.AGGREGATE_FILE_OBJECT);
        payload.put("aggregateId", file == null || file.id() == null ? null : String.valueOf(file.id()));
        payload.put("aggregateVersion", aggregateVersion(eventType));
        payload.put("payload", attributes);
        payload.put("attributes", attributes);
        payload.put("payloadDigest", digest(attributes));
        return payload;
    }

    private long aggregateVersion(String eventType) {
        return FilePlatformEventTypes.FILE_OBJECT_DELETED.equals(eventType) ? 2L : 1L;
    }

    private String digest(Map<String, Object> attributes) {
        try {
            return EventPayloadDigests.sha256(objectMapper.writeValueAsString(attributes));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("File event payload cannot be serialized", exception);
        }
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
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApiProvider == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user resolver is unavailable");
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
        return new TrustedActor(snapshot.userId(), snapshot.userUuid().trim(), simulatedRoleId);
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private record TrustedActor(Long userId, String userUuid, Long simulatedRoleId) {
    }
}
