package com.lumira.file.processing;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.lang.Nullable;
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
    public static final int MAX_CLAIM_LIMIT = 100;
    private static final int MAX_RETRY_COUNT = 5;
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_ERROR_LENGTH = 512;

    private final JdbcTemplate jdbcTemplate;
    @Nullable
    private final FileSecurityScanProcessor securityScanProcessor;
    @Nullable
    private final FileThumbnailProcessor thumbnailProcessor;
    @Nullable
    private final FileOcrProcessor ocrProcessor;
    @Nullable
    private final FileTextExtractionProcessor textExtractionProcessor;
    @Nullable
    private final FileAiParseProcessor aiParseProcessor;
    @Nullable
    private final FileProcessingMetrics processingMetrics;
    private final FileOwnerIdentityVerifier ownerIdentityVerifier;

    @Autowired
    public FileProcessingTaskService(
            JdbcTemplate jdbcTemplate,
            @Nullable
            @Lazy
            FileSecurityScanProcessor securityScanProcessor,
            @Nullable
            @Lazy
            FileThumbnailProcessor thumbnailProcessor,
            @Nullable
            @Lazy
            FileOcrProcessor ocrProcessor,
            @Nullable
            @Lazy
            FileTextExtractionProcessor textExtractionProcessor,
            @Nullable
            @Lazy
            FileAiParseProcessor aiParseProcessor,
            @Nullable
            FileProcessingMetrics processingMetrics,
            FileOwnerIdentityVerifier ownerIdentityVerifier
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityScanProcessor = securityScanProcessor;
        this.thumbnailProcessor = thumbnailProcessor;
        this.ocrProcessor = ocrProcessor;
        this.textExtractionProcessor = textExtractionProcessor;
        this.aiParseProcessor = aiParseProcessor;
        this.processingMetrics = processingMetrics;
        this.ownerIdentityVerifier = ownerIdentityVerifier;
    }

    public FileProcessingTaskService(
            JdbcTemplate jdbcTemplate,
            @Nullable @Lazy FileSecurityScanProcessor securityScanProcessor,
            @Nullable @Lazy FileThumbnailProcessor thumbnailProcessor,
            @Nullable @Lazy FileOcrProcessor ocrProcessor,
            @Nullable @Lazy FileTextExtractionProcessor textExtractionProcessor,
            @Nullable @Lazy FileAiParseProcessor aiParseProcessor,
            @Nullable FileProcessingMetrics processingMetrics
    ) {
        this(
                jdbcTemplate,
                securityScanProcessor,
                thumbnailProcessor,
                ocrProcessor,
                textExtractionProcessor,
                aiParseProcessor,
                processingMetrics,
                null
        );
    }

    public int processPendingTasks(int limit) {
        int processed = 0;
        for (ProcessingTask task : claimPendingTasks(limit)) {
            Instant startedAt = Instant.now();
            try {
                requireTrustedProcessingTask(task);
                process(task);
                markSucceeded(task);
                recordSucceeded(task.taskType(), Duration.between(startedAt, Instant.now()));
                processed++;
            } catch (RuntimeException exception) {
                markFailed(task, exception.getMessage());
                recordFailed(task.taskType(), Duration.between(startedAt, Instant.now()), exception);
            }
        }
        return processed;
    }

    public List<ProcessingTask> claimPendingTasks(int limit) {
        int normalizedLimit = requireClaimLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(15);
        jdbcTemplate.update(
                """
                        update file_processing_task t
                        join (
                            select t.id
                            from file_processing_task t
                            join file_object fo
                              on fo.id = t.file_id
                             and fo.deleted = 0
                             and (
                                   (t.task_type = 'SECURITY_SCAN' and fo.status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN'))
                                   or (t.task_type <> 'SECURITY_SCAN' and fo.status in ('ENABLED', 'CLEAN'))
                             )
                             and fo.uploaded_by is not null
                             and fo.uploaded_by > 0
                             and t.created_by = fo.uploaded_by
                             and t.created_by_uuid = fo.uploaded_by_uuid
                            where t.deleted = 0
                              and (
                                    t.status = ?
                                    or (t.status = ? and (t.next_retry_at is null or t.next_retry_at <= ?))
                                    or (t.status = ? and t.claim_expires_at is not null and t.claim_expires_at <= ?)
                              )
                            order by t.priority desc, t.created_at asc, t.id asc
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
                        select id, file_id as fileId, task_type as taskType,
                               status, priority, retry_count as retryCount, next_retry_at as nextRetryAt,
                               t.claimed_at as claimedAt, t.completed_at as completedAt, t.last_error as lastError,
                               fo.uploaded_by as createdBy, t.created_by_uuid as createdByUserUuid,
                               t.created_at as createdAt, t.updated_by as updatedBy,
                               t.updated_at as updatedAt, t.claim_token as claimToken
                        from file_processing_task t
                        join file_object fo
                          on fo.id = t.file_id
                         and fo.deleted = 0
                         and (
                               (t.task_type = 'SECURITY_SCAN' and fo.status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN'))
                               or (t.task_type <> 'SECURITY_SCAN' and fo.status in ('ENABLED', 'CLEAN'))
                         )
                             and fo.uploaded_by is not null
                             and fo.uploaded_by > 0
                             and t.created_by = fo.uploaded_by
                             and t.created_by_uuid = fo.uploaded_by_uuid
                        where t.deleted = 0 and t.claim_token = ?
                        order by t.priority desc, t.created_at asc, t.id asc
                """,
                (rs, rowNum) -> new ProcessingTask(
                        rs.getLong("id"),
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
                        rs.getString("createdByUserUuid"),
                        rs.getObject("createdAt", LocalDateTime.class),
                        rs.getLong("updatedBy"),
                        rs.getObject("updatedAt", LocalDateTime.class),
                        rs.getString("claimToken")
                ),
                claimToken
        );
    }

    public void markSucceeded(ProcessingTask task) {
        if (task == null || task.id() == null || task.id() <= 0 || !isTrustedClaimToken(task.claimToken())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                """
                        update file_processing_task
                        set status = ?, completed_at = ?, last_error = null, next_retry_at = null,
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?, updated_by_uuid = ?
                        where id = ? and claim_token = ? and deleted = 0 and status = 'PROCESSING'
                          and file_id = ?
                          and task_type = ?
                          and created_by = ?
                          and created_by_uuid = ?
                        """,
                STATUS_SUCCEEDED,
                now,
                now,
                requireTaskOwnerId(task),
                requireTaskOwnerUuid(task),
                task.id(),
                task.claimToken(),
                task.fileId(),
                task.taskType(),
                requireTaskOwnerId(task),
                requireTaskOwnerUuid(task)
        );
        recordClaimMismatchIfNeeded(updated, task, "markSucceeded");
    }

    public void markSucceeded(Long taskId, Long userId) {
        throw new IllegalStateException("File processing task owner UUID is required");
    }

    public void markSucceeded(Long taskId, Long userId, String claimToken) {
        throw new IllegalStateException("File processing task owner UUID is required");
    }

    private int requireClaimLimit(int limit) {
        if (limit < 1 || limit > MAX_CLAIM_LIMIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid file processing task claim limit");
        }
        return limit;
    }

    public void markFailed(ProcessingTask task, String errorMessage) {
        if (task == null || task.id() == null || task.id() <= 0 || !isTrustedClaimToken(task.claimToken())) {
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
                            claim_token = null, claim_expires_at = null, updated_at = ?, updated_by = ?, updated_by_uuid = ?
                        where id = ? and claim_token = ? and deleted = 0 and status = 'PROCESSING'
                          and file_id = ?
                          and task_type = ?
                          and created_by = ?
                          and created_by_uuid = ?
                          and retry_count = ?
                        """,
                deadLetter ? STATUS_DEAD_LETTER : STATUS_FAILED,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount)),
                truncate(errorMessage),
                now,
                taskOwnerIdOrNull(task),
                taskOwnerUuidOrNull(task),
                task.id(),
                task.claimToken(),
                task.fileId(),
                task.taskType(),
                taskOwnerIdOrNull(task),
                taskOwnerUuidOrNull(task),
                retryCount
        );
        recordClaimMismatchIfNeeded(updated, task, "markFailed");
    }

    private void recordClaimMismatchIfNeeded(int updated, ProcessingTask task, String operation) {
        if (updated > 0) {
            return;
        }
        log.warn("File processing task claim mismatch operation={} taskId={} taskType={}",
                operation, task.id(), task.taskType());
        recordClaimMismatch(task.taskType(), operation);
    }

    private void process(ProcessingTask task) {
        if (TASK_SECURITY_SCAN.equals(task.taskType())) {
            requireProcessor(securityScanProcessor, task.taskType(), FileSecurityScanProcessor.class.getSimpleName());
            securityScanProcessor.scan(task.fileId(), requireTaskOwnerId(task), requireTaskOwnerUuid(task));
            return;
        }
        if (TASK_THUMBNAIL.equals(task.taskType())) {
            requireProcessor(thumbnailProcessor, task.taskType(), FileThumbnailProcessor.class.getSimpleName());
            thumbnailProcessor.generateThumbnail(task.fileId(), requireTaskOwnerId(task), requireTaskOwnerUuid(task));
            return;
        }
        if (TASK_OCR.equals(task.taskType())) {
            requireProcessor(ocrProcessor, task.taskType(), FileOcrProcessor.class.getSimpleName());
            ocrProcessor.extractImageText(task.fileId(), requireTaskOwnerId(task), requireTaskOwnerUuid(task));
            return;
        }
        if (TASK_TEXT_EXTRACT.equals(task.taskType())) {
            requireProcessor(textExtractionProcessor, task.taskType(), FileTextExtractionProcessor.class.getSimpleName());
            textExtractionProcessor.extractText(task.fileId(), requireTaskOwnerId(task), requireTaskOwnerUuid(task));
            return;
        }
        if (TASK_AI_PARSE.equals(task.taskType())) {
            requireProcessor(aiParseProcessor, task.taskType(), FileAiParseProcessor.class.getSimpleName());
            aiParseProcessor.prepareForAiParse(task.fileId(), requireTaskOwnerId(task), requireTaskOwnerUuid(task));
            return;
        }
        throw new IllegalStateException("File processing task processor is not implemented: " + task.taskType());
    }

    private void requireTrustedProcessingTask(ProcessingTask task) {
        if (task == null
                || task.id() == null
                || task.id() <= 0
                || task.fileId() == null
                || task.fileId() <= 0
                || !STATUS_PROCESSING.equals(task.status())
                || !isTrustedClaimToken(task.claimToken())
                || task.retryCount() == null
                || task.retryCount() < 0
                || task.retryCount() >= MAX_RETRY_COUNT
                || task.createdBy() == null
                || task.createdBy() <= 0
                || !StringUtils.hasText(task.createdByUserUuid())
                || !isKnownTaskType(task.taskType())) {
            throw new IllegalStateException("File processing task row is invalid");
        }
        if (ownerIdentityVerifier == null) {
            throw new IllegalStateException("File owner identity resolver is unavailable");
        }
        ownerIdentityVerifier.requireEnabledOwner(task.createdBy(), task.createdByUserUuid());
    }

    private boolean isKnownTaskType(String taskType) {
        return TASK_SECURITY_SCAN.equals(taskType)
                || TASK_THUMBNAIL.equals(taskType)
                || TASK_OCR.equals(taskType)
                || TASK_TEXT_EXTRACT.equals(taskType)
                || TASK_AI_PARSE.equals(taskType);
    }

    private boolean isTrustedClaimToken(String claimToken) {
        if (!StringUtils.hasText(claimToken) || claimToken.length() > 128) {
            return false;
        }
        for (int i = 0; i < claimToken.length(); i++) {
            char ch = claimToken.charAt(i);
            if (ch < 33 || ch > 126) {
                return false;
            }
        }
        return true;
    }

    private Long requireTaskOwnerId(ProcessingTask task) {
        if (task == null) {
            throw new IllegalStateException("File processing task is required");
        }
        return requireUserId(task.createdBy());
    }

    private String requireTaskOwnerUuid(ProcessingTask task) {
        if (task == null || !StringUtils.hasText(task.createdByUserUuid())) {
            throw new IllegalStateException("File processing task owner UUID is required");
        }
        return task.createdByUserUuid().trim();
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("File processing task owner is required");
        }
        return userId;
    }

    private Long taskOwnerIdOrNull(ProcessingTask task) {
        if (task == null || task.createdBy() == null || task.createdBy() <= 0) {
            return null;
        }
        return task.createdBy();
    }

    private String taskOwnerUuidOrNull(ProcessingTask task) {
        if (task == null || !StringUtils.hasText(task.createdByUserUuid())) {
            return null;
        }
        return task.createdByUserUuid().trim();
    }

    private void requireProcessor(@Nullable Object processor, String taskType, String processorName) {
        if (processor != null) {
            return;
        }
        throw new IllegalStateException(
                "File processing task processor is not available for task type "
                        + taskType
                        + ": "
                        + processorName
        );
    }

    private void recordSucceeded(String taskType, Duration duration) {
        if (processingMetrics != null) {
            processingMetrics.recordSucceeded(taskType, duration);
        }
    }

    private void recordFailed(String taskType, Duration duration, RuntimeException exception) {
        if (processingMetrics != null) {
            processingMetrics.recordFailed(taskType, duration, exception);
        }
    }

    private void recordClaimMismatch(String taskType, String operation) {
        if (processingMetrics != null) {
            processingMetrics.recordClaimMismatch(taskType, operation);
        }
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
            String createdByUserUuid,
            LocalDateTime createdAt,
            Long updatedBy,
            LocalDateTime updatedAt,
            String claimToken
    ) {
        public ProcessingTask(
                Long id,
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
            this(id, fileId, taskType, status, priority, retryCount, nextRetryAt,
                    claimedAt, completedAt, lastError, createdBy, null, createdAt, updatedBy, updatedAt, null);
        }

        public ProcessingTask(
                Long id,
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
                String createdByUserUuid,
                LocalDateTime createdAt,
                Long updatedBy,
                LocalDateTime updatedAt
        ) {
            this(id, fileId, taskType, status, priority, retryCount, nextRetryAt,
                    claimedAt, completedAt, lastError, createdBy, createdByUserUuid, createdAt, updatedBy, updatedAt, null);
        }

        ProcessingTask withStatus(String status) {
            return new ProcessingTask(id, fileId, taskType, status, priority, retryCount,
                    nextRetryAt, claimedAt, completedAt, lastError, createdBy, createdByUserUuid, createdAt, updatedBy, updatedAt, claimToken);
        }
    }
}
