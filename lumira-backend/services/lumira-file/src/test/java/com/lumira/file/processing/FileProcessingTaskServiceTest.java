package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FileProcessingTaskServiceTest {

    @Test
    void claimPendingTasks_shouldClaimRowsWithConditionalStatusUpdate() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<FileProcessingTaskService.ProcessingTask>>any(), anyString()))
                .thenAnswer(invocation -> {
                    RowMapper<FileProcessingTaskService.ProcessingTask> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(99L);
                    when(resultSet.getLong("fileId")).thenReturn(3001L);
                    when(resultSet.getString("taskType")).thenReturn(FileProcessingTaskService.TASK_SECURITY_SCAN);
                    when(resultSet.getString("status")).thenReturn(FileProcessingTaskService.STATUS_PROCESSING);
                    when(resultSet.getInt("priority")).thenReturn(100);
                    when(resultSet.getInt("retryCount")).thenReturn(0);
                    when(resultSet.getObject(eq("nextRetryAt"), eq(LocalDateTime.class))).thenReturn(null);
                    when(resultSet.getObject(eq("claimedAt"), eq(LocalDateTime.class))).thenReturn(null);
                    when(resultSet.getObject(eq("completedAt"), eq(LocalDateTime.class))).thenReturn(null);
                    when(resultSet.getString("lastError")).thenReturn(null);
                    when(resultSet.getLong("createdBy")).thenReturn(2001L);
                    when(resultSet.getString("createdByUserUuid")).thenReturn("user-uuid-2001");
                    when(resultSet.getObject(eq("createdAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
                    when(resultSet.getLong("updatedBy")).thenReturn(3002L);
                    when(resultSet.getObject(eq("updatedAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
                    when(resultSet.getString("claimToken")).thenReturn("claim-token");
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        var service = service(jdbcTemplate);

        List<FileProcessingTaskService.ProcessingTask> tasks = service.claimPendingTasks(10);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).status()).isEqualTo(FileProcessingTaskService.STATUS_PROCESSING);
    }

    @Test
    void claimPendingTasks_shouldUseValidatedLimitAndQueueOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> querySql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(
                querySql.capture(),
                Mockito.<RowMapper<FileProcessingTaskService.ProcessingTask>>any(),
                anyString()
        )).thenAnswer(invocation -> {
                    RowMapper<FileProcessingTaskService.ProcessingTask> mapper = invocation.getArgument(1);
                    return mapSingleTask(mapper);
                }
        );
        FileProcessingTaskService service = new FileProcessingTaskService(jdbcTemplate, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        List<FileProcessingTaskService.ProcessingTask> tasks = service.claimPendingTasks(FileProcessingTaskService.MAX_CLAIM_LIMIT);

        assertThat(tasks).hasSize(1);
        verify(jdbcTemplate).update(
                updateSql.capture(),
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()
        );
        assertThat(updateSql.getValue())
                .contains("from file_processing_task")
                .contains("join file_object fo")
                .contains("fo.id = t.file_id")
                .contains("fo.deleted = 0")
                .contains("t.task_type = 'SECURITY_SCAN' and fo.status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN')")
                .contains("t.task_type <> 'SECURITY_SCAN' and fo.status in ('ENABLED', 'CLEAN')")
                .contains("fo.uploaded_by is not null")
                .contains("t.created_by = fo.uploaded_by")
                .contains("t.created_by_uuid = fo.uploaded_by_uuid")
                .contains("join sys_user u")
                .contains("u.id = fo.uploaded_by")
                .contains("u.uuid = fo.uploaded_by_uuid")
                .contains("u.status = 'ENABLED'")
                .contains("u.uuid is not null")
                .contains("t.created_by_uuid = u.uuid")
                .contains("deleted = 0")
                .contains("order by t.priority desc, t.created_at asc, t.id asc");
        assertThat(querySql.getValue())
                .contains("fo.uploaded_by as createdBy")
                .contains("t.created_by_uuid as createdByUserUuid")
                .contains("t.created_by = fo.uploaded_by")
                .contains("t.created_by_uuid = fo.uploaded_by_uuid")
                .contains("join sys_user u")
                .contains("u.uuid = fo.uploaded_by_uuid")
                .contains("u.status = 'ENABLED'")
                .contains("u.uuid is not null")
                .contains("t.created_by_uuid = u.uuid")
                .contains("t.claim_token = ?")
                .contains("from file_processing_task t");
    }

    @Test
    void claimPendingTasks_shouldRejectInvalidLimitBeforeDatabaseAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileProcessingTaskService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.claimPendingTasks(0))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.claimPendingTasks(FileProcessingTaskService.MAX_CLAIM_LIMIT + 1))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(jdbcTemplate, never()).update(anyString(), Mockito.<Object[]>any());
        verify(jdbcTemplate, never()).query(anyString(), Mockito.<RowMapper<FileProcessingTaskService.ProcessingTask>>any(), anyString());
    }

    @Test
    void markFailed_shouldMoveToDeadLetterAfterMaxRetries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        var service = service(jdbcTemplate);
        var task = new FileProcessingTaskService.ProcessingTask(
                99L,
                3001L,
                FileProcessingTaskService.TASK_SECURITY_SCAN,
                FileProcessingTaskService.STATUS_PROCESSING,
                100,
                4,
                null,
                LocalDateTime.now(),
                null,
                null,
                3002L,
                "user-uuid-3002",
                LocalDateTime.now(),
                3002L,
                LocalDateTime.now(),
                "claim-token"
        );

        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgs = ArgumentCaptor.forClass(Object[].class);
        service.markFailed(task, "boom");

        verify(jdbcTemplate).update(updateSql.capture(), updateArgs.capture());
        assertThat(updateSql.getValue()).contains("update file_processing_task");
        assertThat(updateSql.getValue()).contains("where id = ? and claim_token = ? and deleted = 0");
        assertThat(updateArgs.getValue()[0]).isEqualTo(FileProcessingTaskService.STATUS_DEAD_LETTER);
        assertThat(updateArgs.getValue()[1]).isEqualTo(5);
    }

    @Test
    void markFailed_shouldIgnoreTaskWithoutClaimToken() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        var service = service(jdbcTemplate);
        var task = new FileProcessingTaskService.ProcessingTask(
                99L,
                3001L,
                FileProcessingTaskService.TASK_SECURITY_SCAN,
                FileProcessingTaskService.STATUS_PROCESSING,
                100,
                0,
                null,
                LocalDateTime.now(),
                null,
                null,
                2001L,
                "user-uuid-2001",
                LocalDateTime.now(),
                2001L,
                LocalDateTime.now(),
                null
        );

        service.markFailed(task, "boom");

        verify(jdbcTemplate, never()).update(anyString(), Mockito.<Object[]>any());
    }

    @Test
    void markSucceededById_shouldRejectWeakOwnerIdentity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        var service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.markSucceeded(99L, 2001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner UUID");
        assertThatThrownBy(() -> service.markSucceeded(99L, 2001L, "claim-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner UUID");

        verify(jdbcTemplate, never()).update(anyString(), Mockito.<Object[]>any());
    }

    @Test
    void processPendingTasks_shouldMarkSecurityScanSucceededWhenFileExists() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileSecurityScanProcessor securityScanProcessor = mock(FileSecurityScanProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_SECURITY_SCAN, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, securityScanProcessor, mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(securityScanProcessor).scan(3001L, 2001L, "user-uuid-2001");
        assertTaskCompletionUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_SUCCEEDED, FileProcessingTaskService.TASK_SECURITY_SCAN);
    }

    @Test
    void processPendingTasks_shouldGenerateThumbnailAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileThumbnailProcessor thumbnailProcessor = mock(FileThumbnailProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_THUMBNAIL, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, mock(FileSecurityScanProcessor.class), thumbnailProcessor, mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(thumbnailProcessor).generateThumbnail(3001L, 2001L, "user-uuid-2001");
        assertTaskCompletionUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_SUCCEEDED, FileProcessingTaskService.TASK_THUMBNAIL);
    }

    @Test
    void processPendingTasks_shouldExtractImageTextAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileOcrProcessor ocrProcessor = mock(FileOcrProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_OCR, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), ocrProcessor, mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(ocrProcessor).extractImageText(3001L, 2001L, "user-uuid-2001");
        assertTaskCompletionUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_SUCCEEDED, FileProcessingTaskService.TASK_OCR);
    }

    @Test
    void processPendingTasks_shouldFailTaskWhenProcessorIsMissing() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockClaimableTask(jdbcTemplate, "UNKNOWN_TASK", FileProcessingTaskService.STATUS_PENDING, 0);
        var service = service(jdbcTemplate);

        int processed = service.processPendingTasks(10);

        assertThat(processed).isZero();
        assertTaskFailureUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_FAILED, "UNKNOWN_TASK", 0);
    }

    @Test
    void processPendingTasks_shouldRejectUntrustedClaimedTaskBeforeProcessorExecution() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileSecurityScanProcessor securityScanProcessor = mock(FileSecurityScanProcessor.class);
        mockClaimedTask(jdbcTemplate, new FileProcessingTaskService.ProcessingTask(
                99L,
                3001L,
                FileProcessingTaskService.TASK_SECURITY_SCAN,
                FileProcessingTaskService.STATUS_PENDING,
                100,
                0,
                null,
                LocalDateTime.now(),
                null,
                null,
                2001L,
                "user-uuid-2001",
                LocalDateTime.now(),
                3002L,
                LocalDateTime.now(),
                "claim-token"
        ));
        var service = new FileProcessingTaskService(jdbcTemplate, securityScanProcessor, mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(securityScanProcessor, never()).scan(any(), any(), any());
        assertTaskFailureUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_FAILED, FileProcessingTaskService.TASK_SECURITY_SCAN, 0);
    }

    @Test
    void processPendingTasks_shouldRejectExhaustedRetryTaskBeforeProcessorExecution() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileSecurityScanProcessor securityScanProcessor = mock(FileSecurityScanProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_SECURITY_SCAN, FileProcessingTaskService.STATUS_PENDING, 5);
        var service = new FileProcessingTaskService(jdbcTemplate, securityScanProcessor, mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(securityScanProcessor, never()).scan(any(), any(), any());
        assertTaskFailureUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_DEAD_LETTER, FileProcessingTaskService.TASK_SECURITY_SCAN, 5);
    }

    @Test
    void processPendingTasks_shouldRejectMissingOwnerUuidBeforeProcessorExecution() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileSecurityScanProcessor securityScanProcessor = mock(FileSecurityScanProcessor.class);
        mockClaimedTask(jdbcTemplate, new FileProcessingTaskService.ProcessingTask(
                99L,
                3001L,
                FileProcessingTaskService.TASK_SECURITY_SCAN,
                FileProcessingTaskService.STATUS_PROCESSING,
                100,
                0,
                null,
                LocalDateTime.now(),
                null,
                null,
                2001L,
                null,
                LocalDateTime.now(),
                3002L,
                LocalDateTime.now(),
                "claim-token"
        ));
        var service = new FileProcessingTaskService(jdbcTemplate, securityScanProcessor, mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(securityScanProcessor, never()).scan(any(), any(), any());
        assertTaskFailureUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_FAILED, FileProcessingTaskService.TASK_SECURITY_SCAN, 0);
    }

    @Test
    void processPendingTasks_shouldExtractTextAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileTextExtractionProcessor textExtractionProcessor = mock(FileTextExtractionProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_TEXT_EXTRACT, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), textExtractionProcessor, mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(textExtractionProcessor).extractText(3001L, 2001L, "user-uuid-2001");
        assertTaskCompletionUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_SUCCEEDED, FileProcessingTaskService.TASK_TEXT_EXTRACT);
    }

    @Test
    void processPendingTasks_shouldPrepareAiParseArtifactAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileAiParseProcessor aiParseProcessor = mock(FileAiParseProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_AI_PARSE, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), aiParseProcessor, mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(aiParseProcessor).prepareForAiParse(3001L, 2001L, "user-uuid-2001");
        assertTaskCompletionUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_SUCCEEDED, FileProcessingTaskService.TASK_AI_PARSE);
    }

    @Test
    void processPendingTasks_shouldFailTaskWithMissingOwnerBeforeProcessorExecution() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileSecurityScanProcessor securityScanProcessor = mock(FileSecurityScanProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_SECURITY_SCAN, FileProcessingTaskService.STATUS_PENDING, 0, 0L);
        var service = new FileProcessingTaskService(jdbcTemplate, securityScanProcessor, mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(securityScanProcessor, never()).scan(any(), any(), any());
        assertTaskFailureUpdate(jdbcTemplate, FileProcessingTaskService.STATUS_FAILED, FileProcessingTaskService.TASK_SECURITY_SCAN, 0);
    }

    private void assertTaskCompletionUpdate(JdbcTemplate jdbcTemplate, String expectedStatus, String expectedTaskType) {
        CapturedUpdate update = captureTaskUpdate(jdbcTemplate, expectedStatus);
        assertThat(update.sql())
                .contains("file_id = ?")
                .contains("task_type = ?")
                .contains("created_by = ?")
                .contains("created_by_uuid = ?");
        assertThat(update.args())
                .contains(expectedStatus, 99L, "claim-token", 3001L, expectedTaskType, 2001L, "user-uuid-2001");
    }

    private void assertTaskFailureUpdate(JdbcTemplate jdbcTemplate, String expectedStatus, String expectedTaskType, int previousRetryCount) {
        CapturedUpdate update = captureTaskUpdate(jdbcTemplate, expectedStatus);
        assertThat(update.sql())
                .contains("file_id = ?")
                .contains("task_type = ?")
                .contains("created_by = ?")
                .contains("created_by_uuid = ?")
                .contains("retry_count = ?");
        assertThat(update.args())
                .contains(expectedStatus, 99L, "claim-token", 3001L, expectedTaskType, previousRetryCount);
    }

    private CapturedUpdate captureTaskUpdate(JdbcTemplate jdbcTemplate, String expectedStatus) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, Mockito.atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        for (int i = 0; i < sqlCaptor.getAllValues().size(); i++) {
            Object[] args = argsCaptor.getAllValues().get(i);
            if (args.length > 0 && expectedStatus.equals(args[0])) {
                return new CapturedUpdate(sqlCaptor.getAllValues().get(i), args);
            }
        }
        throw new AssertionError("Expected task update with status " + expectedStatus);
    }

    private record CapturedUpdate(String sql, Object[] args) {
    }

    private void mockClaimableTask(JdbcTemplate jdbcTemplate, String taskType, String status, int retryCount) {
        mockClaimableTask(jdbcTemplate, taskType, status, retryCount, 2001L);
    }

    private void mockClaimableTask(JdbcTemplate jdbcTemplate, String taskType, String status, int retryCount, Long createdBy) {
        mockClaimedTask(jdbcTemplate, new FileProcessingTaskService.ProcessingTask(
                99L,
                3001L,
                taskType,
                FileProcessingTaskService.STATUS_PROCESSING,
                100,
                retryCount,
                null,
                LocalDateTime.now(),
                null,
                null,
                createdBy,
                createdBy == null || createdBy <= 0 ? null : "user-uuid-" + createdBy,
                LocalDateTime.now(),
                3002L,
                LocalDateTime.now(),
                "claim-token"
        ));
    }

    private void mockClaimedTask(JdbcTemplate jdbcTemplate, FileProcessingTaskService.ProcessingTask task) {
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<FileProcessingTaskService.ProcessingTask>>any(), anyString()))
                .thenReturn(List.of(task));
    }

    private FileProcessingTaskService service(JdbcTemplate jdbcTemplate) {
        return new FileProcessingTaskService(jdbcTemplate, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));
    }

    private List<FileProcessingTaskService.ProcessingTask> mapSingleTask(RowMapper<FileProcessingTaskService.ProcessingTask> mapper) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(99L);
        when(resultSet.getLong("fileId")).thenReturn(3001L);
        when(resultSet.getString("taskType")).thenReturn(FileProcessingTaskService.TASK_SECURITY_SCAN);
        when(resultSet.getString("status")).thenReturn(FileProcessingTaskService.STATUS_PROCESSING);
        when(resultSet.getInt("priority")).thenReturn(100);
        when(resultSet.getInt("retryCount")).thenReturn(0);
        when(resultSet.getObject(eq("nextRetryAt"), eq(LocalDateTime.class))).thenReturn(null);
        when(resultSet.getObject(eq("claimedAt"), eq(LocalDateTime.class))).thenReturn(null);
        when(resultSet.getObject(eq("completedAt"), eq(LocalDateTime.class))).thenReturn(null);
        when(resultSet.getString("lastError")).thenReturn(null);
        when(resultSet.getLong("createdBy")).thenReturn(2001L);
        when(resultSet.getString("createdByUserUuid")).thenReturn("user-uuid-2001");
        when(resultSet.getObject(eq("createdAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
        when(resultSet.getLong("updatedBy")).thenReturn(3002L);
        when(resultSet.getObject(eq("updatedAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
        when(resultSet.getString("claimToken")).thenReturn("claim-token");
        return List.of(mapper.mapRow(resultSet, 0));
    }

    private FileObjectDTO file(String extension, String mimeType) {
        return new FileObjectDTO(
                3001L,
                2001L,
                "user-uuid-2001",
                "tester",
                "report." + extension,
                "2026/05/report." + extension,
                "LOCAL",
                "local",
                extension,
                mimeType,
                1024L,
                "1KB",
                "2026/05/report." + extension,
                "/api/uploads/2026/05/report." + extension,
                "/api/uploads/2026/05/report." + extension,
                "/api/uploads/2026/05/report." + extension,
                extension.toUpperCase(),
                true,
                "我的文件",
                "report",
                null,
                "ENABLED",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
