package com.lumira.file.processing;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FileProcessingTaskRequestService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformEventOutboxService outboxService;

    public FileProcessingTaskRequestService(JdbcTemplate jdbcTemplate, PlatformEventOutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public int requestTasksForUpload(FileObjectDTO file, Long userId) {
        if (file == null || file.id() == null) {
            return 0;
        }
        int requested = 0;
        for (String taskType : resolveTaskTypes(file)) {
            if (upsertTask(file, taskType, userId) > 0) {
                requested++;
                publishTaskRequested(file, taskType, userId);
            }
        }
        return requested;
    }

    private int upsertTask(FileObjectDTO file, String taskType, Long userId) {
        return jdbcTemplate.update(
                """
                        insert into file_processing_task (
                            file_id, task_type, status, priority, retry_count,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, 0, ?, ?, 0)
                        on duplicate key update
                            deleted = 0,
                            updated_at = current_timestamp,
                            updated_by = values(updated_by)
                        """,
                file.id(),
                taskType,
                FileProcessingTaskService.STATUS_PENDING,
                priority(taskType),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
    }

    private void publishTaskRequested(FileObjectDTO file, String taskType, Long userId) {
        outboxService.recordAfterCommit(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED,
                userId,
                buildTaskEventKey(file.id(), taskType),
                buildTaskPayload(file, taskType, userId)
        );
    }

    private String buildTaskEventKey(Long fileId, String taskType) {
        return FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED
                + ":" + FilePlatformEventTypes.AGGREGATE_FILE_PROCESSING_TASK
                + ":" + (fileId == null ? "none" : fileId)
                + ":" + taskType;
    }

    private Map<String, Object> buildTaskPayload(FileObjectDTO file, String taskType, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("occurredAt", LocalDateTime.now());
        payload.put("userId", userId);
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

    private List<String> resolveTaskTypes(FileObjectDTO file) {
        List<String> taskTypes = new ArrayList<>();
        taskTypes.add(FileProcessingTaskService.TASK_SECURITY_SCAN);
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
}
