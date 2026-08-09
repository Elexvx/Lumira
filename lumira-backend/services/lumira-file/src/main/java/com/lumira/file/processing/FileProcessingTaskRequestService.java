package com.lumira.file.processing;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FileProcessingTaskRequestService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformEventOutboxService outboxService;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;

    public FileProcessingTaskRequestService(JdbcTemplate jdbcTemplate, PlatformEventOutboxService outboxService) {
        this(jdbcTemplate, outboxService, null);
    }

    @Autowired
    public FileProcessingTaskRequestService(
            JdbcTemplate jdbcTemplate,
            PlatformEventOutboxService outboxService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.systemInternalApiProvider = systemInternalApiProvider;
    }

    @Transactional
    public int requestTasksForUpload(FileObjectDTO file, CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted file owner is required");
        }
        return requestTasksForUpload(file, trustedActor(currentUser));
    }

    private Actor trustedActor(CurrentUser currentUser) {
        if (systemInternalApiProvider == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted file owner resolver is unavailable");
        }
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "A trusted file owner is required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted file owner resolver is unavailable");
        }
        SystemUserSnapshotDTO snapshot = systemInternalApi.findUserIdentityById(userId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "File owner does not exist");
        }
        if (!StringUtils.hasText(snapshot.userUuid()) || !snapshot.userUuid().trim().equals(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "File owner identity mismatch");
        }
        if (!StringUtils.hasText(snapshot.status()) || !"ENABLED".equalsIgnoreCase(snapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "File owner is disabled");
        }
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, snapshot.userUuid().trim())
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, snapshot.userUuid().trim(), simulatedRoleId);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "File owner permissions are unavailable");
        }
        return new Actor(snapshot.userId(), snapshot.userUuid().trim(), simulatedRoleId);
    }

    private int requestTasksForUpload(FileObjectDTO file, Actor actor) {
        if (file == null || file.id() == null) {
            return 0;
        }
        Long ownerUserId = resolveOwnerUserId(file, actor);
        if (ownerUserId == null) {
            return 0;
        }
        Actor owner = new Actor(ownerUserId, actor.userUuid(), actor.simulatedRoleId());
        int requested = 0;
        for (String taskType : resolveTaskTypes(file)) {
            if (upsertTask(file, taskType, owner) > 0) {
                requested++;
                publishTaskRequested(file, taskType, owner);
            }
        }
        return requested;
    }

    private Long resolveOwnerUserId(FileObjectDTO file, Actor actor) {
        if (file.id() == null || file.id() <= 0 || actor == null || actor.userId() == null || actor.userId() <= 0
                || !StringUtils.hasText(actor.userUuid())) {
            return null;
        }
        if (file.uploadedBy() == null || file.uploadedBy() <= 0 || !file.uploadedBy().equals(actor.userId())) {
            return null;
        }
        if (!StringUtils.hasText(file.uploadedByUuid()) || !file.uploadedByUuid().trim().equals(actor.userUuid().trim())) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select fo.uploaded_by
                            from file_object fo
                            where fo.id = ?
                              and fo.deleted = 0
                              and fo.status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN')
                              and fo.uploaded_by = ?
                              and fo.uploaded_by_uuid = ?
                            limit 1
                            """,
                    Long.class,
                    file.id(),
                    actor.userId(),
                    actor.userUuid().trim()
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private int upsertTask(FileObjectDTO file, String taskType, Actor owner) {
        return jdbcTemplate.update(
                """
                        insert into file_processing_task (
                            file_id, task_type, status, priority, retry_count,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, 0, ?, ?, ?, ?, 0)
                        on duplicate key update
                            deleted = case
                                when created_by = values(created_by)
                                 and created_by_uuid = values(created_by_uuid)
                                then 0 else deleted end,
                            updated_at = case
                                when created_by = values(created_by)
                                 and created_by_uuid = values(created_by_uuid)
                                then current_timestamp else updated_at end,
                            updated_by = case
                                when created_by = values(created_by)
                                 and created_by_uuid = values(created_by_uuid)
                                then values(updated_by) else updated_by end,
                            updated_by_uuid = case
                                when created_by = values(created_by)
                                 and created_by_uuid = values(created_by_uuid)
                                then values(updated_by_uuid) else updated_by_uuid end
                        """,
                file.id(),
                taskType,
                FileProcessingTaskService.STATUS_PENDING,
                priority(taskType),
                owner.userId(),
                owner.userUuid(),
                owner.userId(),
                owner.userUuid()
        );
    }

    private void publishTaskRequested(FileObjectDTO file, String taskType, Actor actor) {
        outboxService.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED,
                outboxUserId(actor),
                buildTaskEventKey(file.id(), taskType),
                buildTaskPayload(file, taskType, actor)
        );
    }

    private String buildTaskEventKey(Long fileId, String taskType) {
        return FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED
                + ":" + FilePlatformEventTypes.AGGREGATE_FILE_PROCESSING_TASK
                + ":" + (fileId == null ? "none" : fileId)
                + ":" + taskType;
    }

    private Map<String, Object> buildTaskPayload(FileObjectDTO file, String taskType, Actor actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("occurredAt", LocalDateTime.now());
        payload.put("userId", actor.userId());
        if (StringUtils.hasText(actor.userUuid())) {
            payload.put("userUuid", actor.userUuid());
        }
        if (actor.simulatedRoleId() != null) {
            payload.put("simulatedRoleId", actor.simulatedRoleId());
        }
        payload.put("aggregateType", FilePlatformEventTypes.AGGREGATE_FILE_PROCESSING_TASK);
        payload.put("aggregateId", file.id() + ":" + taskType);
        payload.put("attributes", Map.of(
                "fileId", file.id(),
                "taskType", taskType,
                "mimeType", file.mimeType() == null ? "" : file.mimeType(),
                "fileExtension", file.fileExtension() == null ? "" : file.fileExtension(),
                "storagePath", file.storagePath() == null ? "" : file.storagePath()
        ));
        return payload;
    }

    private Long outboxUserId(Actor actor) {
        return actor != null && StringUtils.hasText(actor.userUuid()) ? actor.userId() : null;
    }

    private List<String> resolveTaskTypes(FileObjectDTO file) {
        List<String> taskTypes = new ArrayList<>();
        if (!"CLEAN".equalsIgnoreCase(file.status())) {
            taskTypes.add(FileProcessingTaskService.TASK_SECURITY_SCAN);
        }
        if (isImage(file)) {
            taskTypes.add(FileProcessingTaskService.TASK_THUMBNAIL);
            taskTypes.add(FileProcessingTaskService.TASK_OCR);
        }
        if (isTextExtractable(file)) {
            taskTypes.add(FileProcessingTaskService.TASK_TEXT_EXTRACT);
            taskTypes.add(FileProcessingTaskService.TASK_AI_PARSE);
        }
        return taskTypes.stream().distinct().toList();
    }

    private boolean isImage(FileObjectDTO file) {
        String mimeType = normalize(file.mimeType());
        String extension = normalize(file.fileExtension());
        return mimeType.startsWith("image/")
                || List.of("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(extension);
    }

    private boolean isTextExtractable(FileObjectDTO file) {
        String mimeType = normalize(file.mimeType());
        String extension = normalize(file.fileExtension());
        return mimeType.startsWith("text/")
                || mimeType.contains("pdf")
                || mimeType.contains("word")
                || mimeType.contains("excel")
                || mimeType.contains("powerpoint")
                || List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "markdown", "txt", "csv", "json", "log").contains(extension);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "") : "";
    }

    private int priority(String taskType) {
        return switch (taskType) {
            case FileProcessingTaskService.TASK_SECURITY_SCAN -> 100;
            case FileProcessingTaskService.TASK_THUMBNAIL -> 80;
            case FileProcessingTaskService.TASK_TEXT_EXTRACT -> 70;
            case FileProcessingTaskService.TASK_OCR -> 60;
            case FileProcessingTaskService.TASK_AI_PARSE -> 40;
            default -> 0;
        };
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private record Actor(Long userId, String userUuid, Long simulatedRoleId) {
    }
}
