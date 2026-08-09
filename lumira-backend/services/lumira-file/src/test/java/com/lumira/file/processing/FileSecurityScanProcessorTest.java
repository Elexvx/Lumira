package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.file.config.FileSecurityScanProperties;
import com.lumira.file.config.UploadProperties;
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
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        mockLocation(jdbcTemplate, "LOCAL", source, "txt");
        FileOwnerIdentityVerifier ownerIdentityVerifier = mock(FileOwnerIdentityVerifier.class);
        FileSecurityScanProcessor processor = processor(jdbcTemplate, ownerIdentityVerifier);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(3001L, 2001L, "user-uuid-2001");

        assertThat(result.engine()).isEqualTo(FileSecurityScanProcessor.ENGINE_NAME);
        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_CLEAN);
        assertThat(result.scannedBytes()).isEqualTo("hello Lumira".getBytes(StandardCharsets.ISO_8859_1).length);
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, "\"engine\":\"LUMIRA_INLINE_RULES\"");
        verifyArtifact(jdbcTemplate, "\"verdict\":\"CLEAN\"");
        verify(jdbcTemplate).update(contains("update file_object"), eq("CLEAN"), eq(2001L), eq("user-uuid-2001"), eq(3001L), eq(2001L), eq("user-uuid-2001"));
        verify(ownerIdentityVerifier).requireEnabledOwner(2001L, "user-uuid-2001");
    }

    @Test
    void scan_shouldQuarantineFileWhenEicarSignatureIsDetected() throws Exception {
        Path source = writeFile(
                "2026/06/eicar.txt",
                "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
        );
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        mockLocation(jdbcTemplate, "LOCAL", source, "txt");
        when(jdbcTemplate.update(contains("update file_object"), eq("REJECTED"), eq(2001L), eq("user-uuid-2001"), eq(3001L), eq(2001L), eq("user-uuid-2001")))
                .thenReturn(1);
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(3001L, 2001L, "user-uuid-2001");

        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_THREAT_DETECTED);
        assertThat(result.reason()).isEqualTo("EICAR_TEST_SIGNATURE");
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, "\"verdict\":\"THREAT_DETECTED\"");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), eq("REJECTED"), eq(2001L), eq("user-uuid-2001"), eq(3001L), eq(2001L), eq("user-uuid-2001"));
        assertThat(sqlCaptor.getValue())
                .contains("uploaded_by = ?")
                .contains("uploaded_by_uuid = ?")
                .contains("status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN')");
    }

    @Test
    void scan_shouldFailWhenThreatQuarantineMissesCurrentOwnerSnapshot() throws Exception {
        Path source = writeFile(
                "2026/06/eicar-moved.txt",
                "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
        );
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        mockLocation(jdbcTemplate, "LOCAL", source, "txt");
        when(jdbcTemplate.update(contains("update file_object"), eq("REJECTED"), eq(2001L), eq("user-uuid-2001"), eq(3001L), eq(2001L), eq("user-uuid-2001")))
                .thenReturn(0);
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        assertThatThrownBy(() -> processor.scan(3001L, 2001L, "user-uuid-2001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("File security state changed, please retry");
    }

    @Test
    void scan_shouldRequestReviewForHighRiskExtensionWithoutQuarantine() throws Exception {
        Path source = writeFile("2026/06/install.exe", "MZ placeholder");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        mockLocation(jdbcTemplate, "LOCAL", source, "exe");
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(3001L, 2001L, "user-uuid-2001");

        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_REVIEW_REQUIRED);
        assertThat(result.reason()).isEqualTo("HIGH_RISK_EXTENSION");
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, "\"verdict\":\"REVIEW_REQUIRED\"");
        verify(jdbcTemplate).update(contains("update file_object"), eq("REJECTED"), eq(2001L), eq("user-uuid-2001"), eq(3001L), eq(2001L), eq("user-uuid-2001"));
    }

    @Test
    void scan_shouldStoreUnsupportedVerdictForRemoteStorage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        mockRemoteLocation(jdbcTemplate);
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(3001L, 2001L, "user-uuid-2001");

        assertThat(result.verdict()).isEqualTo(FileSecurityScanProcessor.VERDICT_UNSUPPORTED_STORAGE);
        assertThat(result.reason()).isEqualTo("REMOTE_STORAGE_NOT_SCANNED");
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, "\"verdict\":\"UNSUPPORTED_STORAGE\"");
        verify(jdbcTemplate).update(contains("update file_object"), eq("REJECTED"), eq(2001L), eq("user-uuid-2001"), eq(3001L), eq(2001L), eq("user-uuid-2001"));
    }

    @Test
    void scan_shouldRejectMissingOwnerBeforeWritingArtifact() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockRemoteLocation(jdbcTemplate);
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        assertThatThrownBy(() -> processor.scan(3001L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner UUID is required");
        verify(jdbcTemplate, never()).update(
                anyString(),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_SECURITY_SCAN),
                eq(FileSecurityScanProcessor.ARTIFACT_SECURITY_SCAN_RESULT),
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any(),
                Mockito.any()
        );
    }

    @Test
    void scan_shouldRejectWhenArtifactWriteMissesTrustedSnapshot() throws Exception {
        Path source = writeFile("2026/06/readme-moved.txt", "hello Lumira");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(0);
        mockLocation(jdbcTemplate, "LOCAL", source, "txt");
        FileSecurityScanProcessor processor = processor(jdbcTemplate);

        assertThatThrownBy(() -> processor.scan(3001L, 2001L, "user-uuid-2001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artifact state changed");
    }

    private Path writeFile(String relativePath, String content) throws Exception {
        Path source = tempDir.resolve(relativePath);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content, StandardCharsets.ISO_8859_1);
        return source;
    }

    private FileSecurityScanProcessor processor(JdbcTemplate jdbcTemplate) {
        return processor(jdbcTemplate, mock(FileOwnerIdentityVerifier.class));
    }

    private FileSecurityScanProcessor processor(
            JdbcTemplate jdbcTemplate,
            FileOwnerIdentityVerifier ownerIdentityVerifier
    ) {
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
                selector,
                ownerIdentityVerifier
        );
    }

    private void mockLocation(JdbcTemplate jdbcTemplate, String storageType, Path source, String extension) {
        String objectKey = tempDir.relativize(source).toString().replace('\\', '/');
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn(storageType);
                    when(resultSet.getString("objectKey")).thenReturn(objectKey);
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("fileExtension")).thenReturn(extension);
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }

    private void mockRemoteLocation(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.query(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("OSS");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/remote.pdf");
                    when(resultSet.getString("rootPath")).thenReturn("");
                    when(resultSet.getString("fileExtension")).thenReturn("pdf");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return List.of(mapper.mapRow(resultSet, 0));
                });
    }

    private void verifyArtifact(JdbcTemplate jdbcTemplate, String expectedPayloadSnippet) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_SECURITY_SCAN),
                eq(FileSecurityScanProcessor.ARTIFACT_SECURITY_SCAN_RESULT),
                payloadCaptor.capture(),
                Mockito.anyInt(),
                eq(2001L),
                eq("user-uuid-2001"),
                eq(2001L),
                eq("user-uuid-2001"),
                eq(3001L),
                eq(2001L),
                eq("user-uuid-2001")
        );
        assertThat(sqlCaptor.getValue())
                .contains("from file_object fo")
                .contains("fo.uploaded_by_uuid = ?")
                .contains("fo.status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN')")
                .doesNotContain("sys_user");
        assertThat(payloadCaptor.getValue()).asString().contains(expectedPayloadSnippet);
    }

    private void verifyLocationLookup(JdbcTemplate jdbcTemplate) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), Mockito.<RowMapper<?>>any(), eq(3001L));
        assertThat(sqlCaptor.getValue())
                .contains("fo.status in ('PENDING_SCAN', 'FAILED', 'ENABLED', 'CLEAN')")
                .doesNotContain("sys_user");
    }
}
