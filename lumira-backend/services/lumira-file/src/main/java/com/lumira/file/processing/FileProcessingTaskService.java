package com.lumira.file.processing;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnLumiraAsyncEnabled
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
            FileProcessingMetrics processingMetrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityScanProcessor = securityScanProcessor;
        this.thumbnailProcessor = thumbnailProcessor;
        this.ocrProcessor = ocrProcessor;
        this.textExtractionProcessor = textExtractionProcessor;
        this.aiParseProcessor = aiParseProcessor;
        this.processingMetrics = processingMetrics;
    }

    public int processPendingTasks(int limit) {
        int processed = 0;
        for (ProcessingTask task : claimPendingTasks(limit)) {
            Instant startedAt = Instant.now();
            try {
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
                        select id, file_id as fileId, task_type as taskType,
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
            recordClaimMismatch("UNKNOWN", "markSucceeded");
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
        log.warn("File processing task claim mismatch operation={} taskId={} taskType={}",
                operation, task.id(), task.taskType());
        recordClaimMismatch(task.taskType(), operation);
    }

    private void process(ProcessingTask task) {
        if (TASK_SECURITY_SCAN.equals(task.taskType())) {
            requireProcessor(securityScanProcessor, task.taskType(), FileSecurityScanProcessor.class.getSimpleName());
            securityScanProcessor.scan(task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_THUMBNAIL.equals(task.taskType())) {
            requireProcessor(thumbnailProcessor, task.taskType(), FileThumbnailProcessor.class.getSimpleName());
            thumbnailProcessor.generateThumbnail(task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_OCR.equals(task.taskType())) {
            requireProcessor(ocrProcessor, task.taskType(), FileOcrProcessor.class.getSimpleName());
            ocrProcessor.extractImageText(task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_TEXT_EXTRACT.equals(task.taskType())) {
            requireProcessor(textExtractionProcessor, task.taskType(), FileTextExtractionProcessor.class.getSimpleName());
            textExtractionProcessor.extractText(task.fileId(), task.updatedBy());
            return;
        }
        if (TASK_AI_PARSE.equals(task.taskType())) {
            requireProcessor(aiParseProcessor, task.taskType(), FileAiParseProcessor.class.getSimpleName());
            aiParseProcessor.prepareForAiParse(task.fileId(), task.updatedBy());
            return;
        }
        throw new IllegalStateException("File processing task processor is not implemented: " + task.taskType());
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
                    claimedAt, completedAt, lastError, createdBy, createdAt, updatedBy, updatedAt, null);
        }

        ProcessingTask withStatus(String status) {
            return new ProcessingTask(id, fileId, taskType, status, priority, retryCount,
                    nextRetryAt, claimedAt, completedAt, lastError, createdBy, createdAt, updatedBy, updatedAt, claimToken);
        }
    }
}
