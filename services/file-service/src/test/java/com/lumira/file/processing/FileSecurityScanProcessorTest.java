package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.file.config.UploadProperties;
import com.lumira.file.config.FileSecurityScanProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FileSecurityScanProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void scan_shouldStoreCleanArtifactForLocalFile() throws Exception {
        Path source = writeFile("2026/06/readme.txt", "hello Lumira");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockLocation(jdbcTemplate, "LOCAL", source, "txt");
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(1001L, 3001L, 2001L);

        assertThat(result.engine()).isEqualTo(FileSecurityScanProcessor.ENGINE_NAME);
        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_CLEAN);
        assertThat(result.scannedBytes()).isEqualTo("hello Lumira".getBytes(StandardCharsets.ISO_8859_1).length);
        verifyArtifact(jdbcTemplate, "\"engine\":\"LUMIRA_INLINE_RULES\"");
        verifyArtifact(jdbcTemplate, "\"verdict\":\"CLEAN\"");
        verify(jdbcTemplate, never()).update(anyString(), eq("QUARANTINED"), eq(2001L), eq(1001L), eq(3001L));
    }

    @Test
    void scan_shouldQuarantineFileWhenEicarSignatureIsDetected() throws Exception {
        Path source = writeFile(
                "2026/06/eicar.txt",
                "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
        );
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockLocation(jdbcTemplate, "LOCAL", source, "txt");
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(1001L, 3001L, 2001L);

        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_THREAT_DETECTED);
        assertThat(result.reason()).isEqualTo("EICAR_TEST_SIGNATURE");
        verifyArtifact(jdbcTemplate, "\"verdict\":\"THREAT_DETECTED\"");
        verify(jdbcTemplate).update(anyString(), eq("QUARANTINED"), eq(2001L), eq(1001L), eq(3001L));
    }

    @Test
    void scan_shouldRequestReviewForHighRiskExtensionWithoutQuarantine() throws Exception {
        Path source = writeFile("2026/06/install.exe", "MZ placeholder");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockLocation(jdbcTemplate, "LOCAL", source, "exe");
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(1001L, 3001L, 2001L);

        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_REVIEW_REQUIRED);
        assertThat(result.reason()).isEqualTo("HIGH_RISK_EXTENSION");
        verifyArtifact(jdbcTemplate, "\"verdict\":\"REVIEW_REQUIRED\"");
        verify(jdbcTemplate, never()).update(anyString(), eq("QUARANTINED"), eq(2001L), eq(1001L), eq(3001L));
    }

    @Test
    void scan_shouldStoreUnsupportedVerdictForRemoteStorage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockRemoteLocation(jdbcTemplate);
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(1001L, 3001L, 2001L);

        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_UNSUPPORTED_STORAGE);
        assertThat(result.reason()).isEqualTo("REMOTE_STORAGE_NOT_SCANNED");
        verifyArtifact(jdbcTemplate, "\"verdict\":\"UNSUPPORTED_STORAGE\"");
        verify(jdbcTemplate, never()).update(anyString(), eq("QUARANTINED"), eq(2001L), eq(1001L), eq(3001L));
    }

    private Path writeFile(String relativePath, String content) throws Exception {
        Path source = tempDir.resolve(relativePath);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content, StandardCharsets.ISO_8859_1);
        return source;
    }

    private FileSecurityScanProcessor processor(JdbcTemplate jdbcTemplate) {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        FileSecurityScanProperties securityScanProperties = new FileSecurityScanProperties();
        InlineFileSecurityScanEngine inlineEngine = new InlineFileSecurityScanEngine();
        ClamAvFileSecurityScanEngine clamAvEngine = new ClamAvFileSecurityScanEngine(securityScanProperties);
        FileSecurityScanEngineSelector selector = new FileSecurityScanEngineSelector(securityScanProperties, inlineEngine, clamAvEngine);
        return new FileSecurityScanProcessor(
                jdbcTemplate,
                uploadProperties,
                new FileSecurityScanMetrics(new SimpleMeterRegistry()),
                selector
        );
    }

    private void mockLocation(JdbcTemplate jdbcTemplate, String storageType, Path source, String extension) {
        String objectKey = tempDir.relativize(source).toString().replace('\\', '/');
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<?>>any(), eq(1001L), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn(storageType);
                    when(resultSet.getString("objectKey")).thenReturn(objectKey);
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("fileExtension")).thenReturn(extension);
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }

    private void mockRemoteLocation(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<?>>any(), eq(1001L), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("OSS");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/remote.pdf");
                    when(resultSet.getString("rootPath")).thenReturn("");
                    when(resultSet.getString("fileExtension")).thenReturn("pdf");
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }

    private void verifyArtifact(JdbcTemplate jdbcTemplate, String expectedPayloadSnippet) {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                anyString(),
                eq(1001L),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_SECURITY_SCAN),
                eq(FileSecurityScanProcessor.ARTIFACT_SECURITY_SCAN_RESULT),
                payloadCaptor.capture(),
                Mockito.anyInt(),
                eq(2001L),
                eq(2001L)
        );
        assertThat(payloadCaptor.getValue()).asString().contains(expectedPayloadSnippet);
    }
}
