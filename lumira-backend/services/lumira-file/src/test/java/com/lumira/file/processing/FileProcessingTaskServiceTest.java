package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
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
    void requestTasksForUpload_shouldCreateExpectedTasksAndPublishEvents() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        when(jdbcTemplate.update(anyString(), anyLong(), anyLong(), anyString(), eq(FileProcessingTaskService.STATUS_PENDING), anyInt(), anyLong(), anyLong()))
                .thenReturn(1);
        var service = service(jdbcTemplate, outboxService);

        int requested = service.requestTasksForUpload(file("pdf", "application/pdf"), 2001L);

        assertThat(requested).isEqualTo(3);
        verify(jdbcTemplate, times(3)).update(anyString(), anyLong(), anyLong(), anyString(), eq(FileProcessingTaskService.STATUS_PENDING), anyInt(), anyLong(), anyLong());
        verify(outboxService, times(3)).recordAfterCommit(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq(FilePlatformEventTypes.FILE_PROCESSING_TASK_REQUESTED),
                eq(1001L),
                eq(2001L),
                anyString(),
                any()
        );
    }

    @Test
    void claimPendingTasks_shouldClaimRowsWithConditionalStatusUpdate() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<FileProcessingTaskService.ProcessingTask>>any(), anyString()))
                .thenAnswer(invocation -> {
                    RowMapper<FileProcessingTaskService.ProcessingTask> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(99L);
                    when(resultSet.getLong("tenantId")).thenReturn(1001L);
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
                    when(resultSet.getObject(eq("createdAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
                    when(resultSet.getLong("updatedBy")).thenReturn(2001L);
                    when(resultSet.getObject(eq("updatedAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
                    when(resultSet.getString("claimToken")).thenReturn("claim-token");
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        var service = service(jdbcTemplate, outboxService);

        List<FileProcessingTaskService.ProcessingTask> tasks = service.claimPendingTasks(10);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).status()).isEqualTo(FileProcessingTaskService.STATUS_PROCESSING);
    }

    @Test
    void claimPendingTasks_shouldUseCappedLimitAndQueueOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
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
        FileProcessingTaskService service = new FileProcessingTaskService(jdbcTemplate, outboxService, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        List<FileProcessingTaskService.ProcessingTask> tasks = service.claimPendingTasks(250);

        assertThat(tasks).hasSize(1);
        assertThat(querySql.getValue())
                .contains("from file_processing_task")
                .contains("deleted = 0")
                .contains("claim_token = ?")
                .contains("order by priority desc, created_at asc, id asc")
                .contains("from file_processing_task");
    }

    @Test
    void markFailed_shouldMoveToDeadLetterAfterMaxRetries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        var service = service(jdbcTemplate, outboxService);
        var task = new FileProcessingTaskService.ProcessingTask(
                99L,
                1001L,
                3001L,
                FileProcessingTaskService.TASK_SECURITY_SCAN,
                FileProcessingTaskService.STATUS_PROCESSING,
                100,
                4,
                null,
                LocalDateTime.now(),
                null,
                null,
                2001L,
                LocalDateTime.now(),
                2001L,
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
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        var service = service(jdbcTemplate, outboxService);
        var task = new FileProcessingTaskService.ProcessingTask(
                99L,
                1001L,
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
                LocalDateTime.now(),
                2001L,
                LocalDateTime.now(),
                null
        );

        service.markFailed(task, "boom");

        verify(jdbcTemplate, never()).update(anyString(), Mockito.<Object[]>any());
    }

    @Test
    void markSucceededById_shouldRequireClaimToken() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        var service = service(jdbcTemplate, outboxService);

        service.markSucceeded(99L, 2001L);

        verify(jdbcTemplate, never()).update(anyString(), Mockito.<Object[]>any());
    }

    @Test
    void markSucceededById_shouldUpdateOnlyMatchingClaimToken() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        var service = service(jdbcTemplate, outboxService);
        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgs = ArgumentCaptor.forClass(Object[].class);

        service.markSucceeded(99L, 2001L, "claim-token");

        verify(jdbcTemplate).update(updateSql.capture(), updateArgs.capture());
        assertThat(updateSql.getValue()).contains("where id = ? and claim_token = ? and deleted = 0");
        assertThat(updateArgs.getValue()).contains(99L, "claim-token");
    }

    @Test
    void processPendingTasks_shouldMarkSecurityScanSucceededWhenFileExists() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileSecurityScanProcessor securityScanProcessor = mock(FileSecurityScanProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_SECURITY_SCAN, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, outboxService, securityScanProcessor, mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(securityScanProcessor).scan(1001L, 3001L, 2001L);
        verify(jdbcTemplate).update(anyString(), eq(FileProcessingTaskService.STATUS_SUCCEEDED), any(LocalDateTime.class), any(LocalDateTime.class), eq(2001L), eq(99L), eq("claim-token"));
    }

    @Test
    void processPendingTasks_shouldGenerateThumbnailAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileThumbnailProcessor thumbnailProcessor = mock(FileThumbnailProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_THUMBNAIL, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, outboxService, mock(FileSecurityScanProcessor.class), thumbnailProcessor, mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(thumbnailProcessor).generateThumbnail(1001L, 3001L, 2001L);
        verify(jdbcTemplate).update(anyString(), eq(FileProcessingTaskService.STATUS_SUCCEEDED), any(LocalDateTime.class), any(LocalDateTime.class), eq(2001L), eq(99L), eq("claim-token"));
    }

    @Test
    void processPendingTasks_shouldExtractImageTextAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileOcrProcessor ocrProcessor = mock(FileOcrProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_OCR, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, outboxService, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), ocrProcessor, mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(ocrProcessor).extractImageText(1001L, 3001L, 2001L);
        verify(jdbcTemplate).update(anyString(), eq(FileProcessingTaskService.STATUS_SUCCEEDED), any(LocalDateTime.class), any(LocalDateTime.class), eq(2001L), eq(99L), eq("claim-token"));
    }

    @Test
    void processPendingTasks_shouldFailTaskWhenProcessorIsMissing() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        mockClaimableTask(jdbcTemplate, "UNKNOWN_TASK", FileProcessingTaskService.STATUS_PENDING, 0);
        var service = service(jdbcTemplate, outboxService);

        int processed = service.processPendingTasks(10);

        assertThat(processed).isZero();
        verify(jdbcTemplate).update(anyString(), eq(FileProcessingTaskService.STATUS_FAILED), eq(1), any(LocalDateTime.class), anyString(), any(LocalDateTime.class), eq(2001L), eq(99L), eq("claim-token"));
    }

    @Test
    void processPendingTasks_shouldExtractTextAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileTextExtractionProcessor textExtractionProcessor = mock(FileTextExtractionProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_TEXT_EXTRACT, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, outboxService, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), textExtractionProcessor, mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(textExtractionProcessor).extractText(1001L, 3001L, 2001L);
        verify(jdbcTemplate).update(anyString(), eq(FileProcessingTaskService.STATUS_SUCCEEDED), any(LocalDateTime.class), any(LocalDateTime.class), eq(2001L), eq(99L), eq("claim-token"));
    }

    @Test
    void processPendingTasks_shouldPrepareAiParseArtifactAndMarkTaskSucceeded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileAiParseProcessor aiParseProcessor = mock(FileAiParseProcessor.class);
        mockClaimableTask(jdbcTemplate, FileProcessingTaskService.TASK_AI_PARSE, FileProcessingTaskService.STATUS_PENDING, 0);
        var service = new FileProcessingTaskService(jdbcTemplate, outboxService, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), aiParseProcessor, mock(FileProcessingMetrics.class));

        int processed = service.processPendingTasks(10);

        assertThat(processed).isEqualTo(1);
        verify(aiParseProcessor).prepareForAiParse(1001L, 3001L, 2001L);
        verify(jdbcTemplate).update(anyString(), eq(FileProcessingTaskService.STATUS_SUCCEEDED), any(LocalDateTime.class), any(LocalDateTime.class), eq(2001L), eq(99L), eq("claim-token"));
    }

    private void mockClaimableTask(JdbcTemplate jdbcTemplate, String taskType, String status, int retryCount) {
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<FileProcessingTaskService.ProcessingTask>>any(), anyString()))
                .thenAnswer(invocation -> {
                    RowMapper<FileProcessingTaskService.ProcessingTask> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(99L);
                    when(resultSet.getLong("tenantId")).thenReturn(1001L);
                    when(resultSet.getLong("fileId")).thenReturn(3001L);
                    when(resultSet.getString("taskType")).thenReturn(taskType);
                    when(resultSet.getString("status")).thenReturn(FileProcessingTaskService.STATUS_PROCESSING);
                    when(resultSet.getInt("priority")).thenReturn(100);
                    when(resultSet.getInt("retryCount")).thenReturn(retryCount);
                    when(resultSet.getObject(eq("nextRetryAt"), eq(LocalDateTime.class))).thenReturn(null);
                    when(resultSet.getObject(eq("claimedAt"), eq(LocalDateTime.class))).thenReturn(null);
                    when(resultSet.getObject(eq("completedAt"), eq(LocalDateTime.class))).thenReturn(null);
                    when(resultSet.getString("lastError")).thenReturn(null);
                    when(resultSet.getLong("createdBy")).thenReturn(2001L);
                    when(resultSet.getObject(eq("createdAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
                    when(resultSet.getLong("updatedBy")).thenReturn(2001L);
                    when(resultSet.getObject(eq("updatedAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
                    when(resultSet.getString("claimToken")).thenReturn("claim-token");
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }

    private FileProcessingTaskService service(JdbcTemplate jdbcTemplate, PlatformEventOutboxService outboxService) {
        return new FileProcessingTaskService(jdbcTemplate, outboxService, mock(FileSecurityScanProcessor.class), mock(FileThumbnailProcessor.class), mock(FileOcrProcessor.class), mock(FileTextExtractionProcessor.class), mock(FileAiParseProcessor.class), mock(FileProcessingMetrics.class));
    }

    private List<FileProcessingTaskService.ProcessingTask> mapSingleTask(RowMapper<FileProcessingTaskService.ProcessingTask> mapper) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(99L);
        when(resultSet.getLong("tenantId")).thenReturn(1001L);
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
        when(resultSet.getObject(eq("createdAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
        when(resultSet.getLong("updatedBy")).thenReturn(2001L);
        when(resultSet.getObject(eq("updatedAt"), eq(LocalDateTime.class))).thenReturn(LocalDateTime.now());
        when(resultSet.getString("claimToken")).thenReturn("claim-token");
        return List.of(mapper.mapRow(resultSet, 0));
    }

    private FileObjectDTO file(String extension, String mimeType) {
        return new FileObjectDTO(
                3001L,
                1001L,
                2001L,
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
