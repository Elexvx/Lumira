package com.lumira.file.processing;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FileProcessingTaskService {
    private static final Logger log = LoggerFactory.getLogger(FileProcessingTaskService.class);

    public static final String TASK_SECURITY_SCAN = "SECURITY_SCAN";
    public static final String TASK_THUMBNAIL = "THUMBNAIL";
    public static final String TASK_TEXT_EXTRACT = "TEXT_EXTRACT";
    public static final String TASK_OCR = "OCR";
    public static final String TASK_AI_PARSE = "AI_PARSE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final int MAX_CLAIM_LIMIT = 100;
    private static final int MAX_RETRY_COUNT = 5;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 512;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformEventOutboxService outboxService;
    private final FileSecurityScanProcessor securityScanProcessor;
    private final FileThumbnailProcessor thumbnailProcessor;
    private final FileOcrProcessor ocrProcessor;
    private final FileTextExtractionProcessor textExtractionProcessor;
    private final FileAiParseProcessor aiParseProcessor;
    private final FileProcessingMetrics processingMetrics;

    public FileProcessingTaskService(
            JdbcTemplate jdbcTemplate,
            PlatformEventOutboxService outboxService,
            FileSecurityScanProcessor securityScanProcessor,
            FileThumbnailProcessor thumbnailProcessor,
            FileOcrProcessor ocrProcessor,
            FileTextExtractionProcessor textExtractionProcessor,
            FileAiParseProcessor aiParseProcessor,
            FileProcessingMetrics processingMetrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.securityScanProcessor = securityScanProcessor;
        this.thumbnailProcessor = thumbnailProcessor;
        this.ocrProcessor = ocrProcessor;
        this.textExtractionProcessor = textExtractionProcessor;
        this.aiParseProcessor = aiParseProcessor;
        this.processingMetrics = processingMetrics;
    }

    public int requestTasksForUpload(FileObjectDTO file, Long userId) {
        if (file == null || file.id() == null || file.tenantId() == null) {
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

    public int processPendingTasks(int limit) {
        int processed = 0;
        for (ProcessingTask task : claimPendingTasks(limit)) {
            Instant startedAt = Instant.now();
            try {
                process(task);
                markSucceeded(task);
                processingMetrics.recordSucceeded(task.taskType(), Duration.between(startedAt, Instant.now()));
                processed++;
            } catch (RuntimeException exception) {
                markFailed(task, exception.getMessage());
                processingMetrics.recordFailed(task.taskType(), Duration.between(startedAt, Instant.now()), exception);
            }
        }
        return processed;
    }

    public List<ProcessingTask> claimPendingTasks(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, MAX_CLAIM_LIMIT));
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(15);
        jdbcTemplate.update(
                """
                        update file_processing_task t
                        join (
                            select id
                            from file_processing_task
                            where deleted = 0
                              and (
                                    status = ?
                                    or (status = ? and (next_retry_at is null or next_retry_at <= ?))
                                    or (status = ? and claim_expires_at is not null and claim_expires_at <= ?)
                              )
                            order by priority desc, created_at asc, id asc
                            limit ?
                        ) picked on picked.id = t.id
                        set t.status = ?,
                            t.claimed_at = ?,
                            t.claimed_by = ?,
                            t.claim_token = ?,
                            t.claim_expires_at = ?,
                            t.updated_at = ?
                        where t.deleted = 0
                        """,
                STATUS_PENDING,
                STATUS_FAILED,
                now,
                STATUS_PROCESSING,
                now,
                normalizedLimit,
                STATUS_PROCESSING,
                now,
                workerId(),
                claimToken,
                claimExpiresAt,
                now
        );
        return jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, file_id as fileId, task_type as taskType,
                               status, priority, retry_count as retryCount, next_retry_at as nextRetryAt,
                               claimed_at as claimedAt, completed_at as completedAt, last_error as lastError,
                               created_by as createdBy, created_at as createdAt, updated_by as updatedBy,
                               updated_at as updatedAt, claim_token as claimToken
                        from file_processing_task
                        where deleted = 0 and claim_token = ?
                        order by priority desc, created_at asc, id asc
                        """,
                (rs, rowNum) -> new ProcessingTask(
                        rs.getLong("id"),
                        rs.getLong("tenantId"),
                        rs.getLong("fileId"),
                        rs.getString("taskType"),
                        rs.getString("status"),
                        rs.getInt("priority"),
                        rs.getInt("retryCount"),
                        rs.getObject("nextRetryAt", LocalDateTime.class),
                        rs.getObject("claimedAt", LocalDateTime.class),
                        rs.getObject("completedAt", LocalDateTime.class),
                        rs.getString("lastError"),
                        rs.getLong("createdBy"),
                        rs.getObject("createdAt", LocalDateTime.class),
                        rs.getLong("updatedBy"),
                        rs.getObject("updatedAt", LocalDateTime.class),
                        rs.getString("claimToken")
                ),
                claimToken
        );
    }

    public void markSucceeded(ProcessingTask task) {
        if (task == null || task.id() == null || !StringUtils.hasText(task.claimToken())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                """
                        update file_processing_task
                        set status = ?, completed_at = ?, last_error = null, next_retry_at = null,
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?
                        where id = ? and claim_token = ? and deleted = 0 and status = 'PROCESSING'
                        """,
                STATUS_SUCCEEDED,
                now,
                now,
                task.updatedBy() == null ? 0L : task.updatedBy(),
                task.id(),
                task.claimToken()
        );
        recordClaimMismatchIfNeeded(updated, task, "markSucceeded");
    }

    public void markSucceeded(Long taskId, Long userId) {
        markSucceeded(taskId, userId, null);
    }

    public void markSucceeded(Long taskId, Long userId, String claimToken) {
        if (taskId == null || !StringUtils.hasText(claimToken)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                """
                        update file_processing_task
                        set status = ?, completed_at = ?, last_error = null, next_retry_at = null,
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?
                        where id = ? and claim_token = ? and deleted = 0 and status = 'PROCESSING'
                        """,
                STATUS_SUCCEEDED,
                now,
                now,
                userId == null ? 0L : userId,
                taskId,
                claimToken
        );
        if (updated == 0) {
            log.warn("File processing task markSucceeded claim mismatch taskId={}", taskId);
            processingMetrics.recordClaimMismatch("UNKNOWN", "markSucceeded");
        }
    }

    public void markFailed(ProcessingTask task, String errorMessage) {
        if (task == null || task.id() == null || !StringUtils.hasText(task.claimToken())) {
            return;
        }
        int retryCount = task.retryCount() == null ? 0 : task.retryCount();
        int nextRetryCount = retryCount + 1;
        boolean deadLetter = nextRetryCount >= MAX_RETRY_COUNT;
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                """
                        update file_processing_task
                        set status = ?, retry_count = ?, next_retry_at = ?, last_error = ?,
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?
                        where id = ? and claim_token = ? and deleted = 0 and status = 'PROCESSING'
                        """,
                deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncate(errorMessage),
                now,
                task.updatedBy() == null ? 0L : task.updatedBy(),
                task.id(),
                task.claimToken()
        );
        recordClaimMismatchIfNeeded(updated, task, "markFailed");
    }

    private void recordClaimMismatchIfNeeded(int updated, ProcessingTask task, String operation) {
        if (updated > 0) {
            return;
        }
        log.warn("File processing task claim mismatch operation={} taskId={} tenantId={} taskType={}",
                operation, task.id(), task.tenantId(), task.taskType());
        processingMetrics.recordClaimMismatch(task.taskType(), operation);
    }

    private void process(ProcessingTask task) {
        if (TASK_SECURITY_SCAN.equals(task.taskType())) {
            securityScanProcessor.scan(task.tenantId(), task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_THUMBNAIL.equals(task.taskType())) {
            thumbnailProcessor.generateThumbnail(task.tenantId(), task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_OCR.equals(task.taskType())) {
            ocrProcessor.extractImageText(task.tenantId(), task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_TEXT_EXTRACT.equals(task.taskType())) {
            textExtractionProcessor.extractText(task.tenantId(), task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_AI_PARSE.equals(task.taskType())) {
            aiParseProcessor.prepareForAiParse(task.tenantId(), task.fileId(), task.updatedBy());
            return;
        }
        throw new IllegalStateException("File processing task processor is not implemented: " + task.taskType());
    }

    private int upsertTask(FileObjectDTO file, String taskType, Long userId) {
        return jdbcTemplate.update(
                """
                        insert into file_processing_task (
                            tenant_id, file_id, task_type, status, priority, retry_count,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, 0, ?, ?, 0)
                        on duplicate key update
                            deleted = 0,
                            updated_at = current_timestamp,
                            updated_by = values(updated_by)
                        """,
                file.tenantId(),
                file.id(),
                taskType,
                STATUS_PENDING,
                priority(taskType),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
    }

    private void publishTaskRequested(FileObjectDTO file, String taskType, Long userId) {
        outboxService.recordAfterCommit(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED,
                file.tenantId(),
                userId,
                buildTaskEventKey(file.tenantId(), file.id(), taskType),
                buildTaskPayload(file, taskType, userId)
        );
    }

    private String buildTaskEventKey(Long tenantId, Long fileId, String taskType) {
        return FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED
                + ":" + (tenantId == null ? "unknown" : tenantId)
                + ":" + FilePlatformEventTypes.AGGREGATE_FILE_PROCESSING_TASK
                + ":" + (fileId == null ? "none" : fileId)
                + ":" + taskType;
    }

    private Map<String, Object> buildTaskPayload(FileObjectDTO file, String taskType, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("occurredAt", LocalDateTime.now());
        payload.put("tenantId", file.tenantId());
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
        taskTypes.add(TASK_SECURITY_SCAN);
        if (isImage(file)) {
            taskTypes.add(TASK_THUMBNAIL);
            taskTypes.add(TASK_OCR);
        }
        if (isTextExtractable(file)) {
            taskTypes.add(TASK_TEXT_EXTRACT);
            taskTypes.add(TASK_AI_PARSE);
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
            case TASK_SECURITY_SCAN -> 100;
            case TASK_THUMBNAIL -> 80;
            case TASK_TEXT_EXTRACT -> 70;
            case TASK_OCR -> 60;
            case TASK_AI_PARSE -> 40;
            default -> 0;
        };
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 1), MAX_RETRY_COUNT);
        return Math.min(MAX_RETRY_DELAY_SECONDS, (long) Math.pow(2, exponent));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private String workerId() {
        return System.getProperty("lumira.worker.id", java.net.InetAddress.getLoopbackAddress().getHostName());
    }

    public record ProcessingTask(
            Long id,
            Long tenantId,
            Long fileId,
            String taskType,
            String status,
            Integer priority,
            Integer retryCount,
            LocalDateTime nextRetryAt,
            LocalDateTime claimedAt,
            LocalDateTime completedAt,
            String lastError,
            Long createdBy,
            LocalDateTime createdAt,
            Long updatedBy,
            LocalDateTime updatedAt,
            String claimToken
    ) {
        public ProcessingTask(
                Long id,
                Long tenantId,
                Long fileId,
                String taskType,
                String status,
                Integer priority,
                Integer retryCount,
                LocalDateTime nextRetryAt,
                LocalDateTime claimedAt,
                LocalDateTime completedAt,
                String lastError,
                Long createdBy,
                LocalDateTime createdAt,
                Long updatedBy,
                LocalDateTime updatedAt
        ) {
            this(id, tenantId, fileId, taskType, status, priority, retryCount, nextRetryAt,
                    claimedAt, completedAt, lastError, createdBy, createdAt, updatedBy, updatedAt, null);
        }

        ProcessingTask withStatus(String status) {
            return new ProcessingTask(id, tenantId, fileId, taskType, status, priority, retryCount,
                    nextRetryAt, claimedAt, completedAt, lastError, createdBy, createdAt, updatedBy, updatedAt, claimToken);
        }
    }
}
