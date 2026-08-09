package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.file.config.UploadProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FileOcrProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractImageText_shouldStoreSkippedArtifactWhenOcrIsDisabled() throws Exception {
        Path source = writeImagePlaceholder("2026/06/image.png");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockLocation(jdbcTemplate, source, "image/png", "png");
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        FileOwnerIdentityVerifier ownerIdentityVerifier = mock(FileOwnerIdentityVerifier.class);
        FileOcrProcessor processor = processor(jdbcTemplate, new DisabledFileOcrEngine(), ownerIdentityVerifier);

        FileOcrProcessor.OcrResult result = processor.extractImageText(3001L, 2001L, "user-uuid-2001");

        assertThat(result.engine()).isEqualTo(DisabledFileOcrEngine.ENGINE_NAME);
        assertThat(result.status()).isEqualTo(FileOcrProcessor.STATUS_SKIPPED);
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, FileOcrProcessor.ARTIFACT_OCR_RESULT, "\"status\":\"SKIPPED\"");
        verify(ownerIdentityVerifier).requireEnabledOwner(2001L, "user-uuid-2001");
    }

    @Test
    void extractImageText_shouldStoreOcrResultAndTextContentWhenTextIsExtracted() throws Exception {
        Path source = writeImagePlaceholder("2026/06/invoice.jpg");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockLocation(jdbcTemplate, source, "image/jpeg", "jpg");
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        FileOcrEngine engine = new FileOcrEngine() {
            @Override
            public String engineName() {
                return "FAKE_OCR";
            }

            @Override
            public OcrEngineResult extract(FileOcrRequest request) {
                return new OcrEngineResult(engineName(), FileOcrProcessor.STATUS_EXTRACTED, "", "hello\n\nLumira OCR");
            }
        };
        FileOcrProcessor processor = processor(jdbcTemplate, engine);

        FileOcrProcessor.OcrResult result = processor.extractImageText(3001L, 2001L, "user-uuid-2001");

        assertThat(result.engine()).isEqualTo("FAKE_OCR");
        assertThat(result.status()).isEqualTo(FileOcrProcessor.STATUS_EXTRACTED);
        assertThat(result.storedCharacters()).isEqualTo("hello\n\nLumira OCR".length());
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, FileOcrProcessor.ARTIFACT_OCR_RESULT, "\"engine\":\"FAKE_OCR\"");
        verifyArtifact(jdbcTemplate, FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT, "hello\n\nLumira OCR");
    }

    private Path writeImagePlaceholder(String relativePath) throws Exception {
        Path source = tempDir.resolve(relativePath);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "not a real image but enough for OCR source lookup", StandardCharsets.UTF_8);
        return source;
    }

    private FileOcrProcessor processor(JdbcTemplate jdbcTemplate, FileOcrEngine engine) {
        return processor(jdbcTemplate, engine, mock(FileOwnerIdentityVerifier.class));
    }

    private FileOcrProcessor processor(
            JdbcTemplate jdbcTemplate,
            FileOcrEngine engine,
            FileOwnerIdentityVerifier ownerIdentityVerifier
    ) {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        FileOcrEngineSelector selector = mock(FileOcrEngineSelector.class);
        when(selector.select()).thenReturn(engine);
        return new FileOcrProcessor(jdbcTemplate, uploadProperties, selector, ownerIdentityVerifier);
    }

    private void mockLocation(JdbcTemplate jdbcTemplate, Path source, String contentType, String extension) {
        String objectKey = tempDir.relativize(source).toString().replace('\\', '/');
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn(objectKey);
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn(contentType);
                    when(resultSet.getString("fileExtension")).thenReturn(extension);
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
    }

    @Test
    void extractImageText_shouldRejectMissingOwnerUuid() {
        FileOcrProcessor processor = processor(mock(JdbcTemplate.class), new DisabledFileOcrEngine());

        assertThatThrownBy(() -> processor.extractImageText(3001L, 2001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner UUID is required");
    }

    @Test
    void extractImageText_shouldRejectWhenArtifactWriteMissesTrustedSnapshot() throws Exception {
        Path source = writeImagePlaceholder("2026/06/image.png");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockLocation(jdbcTemplate, source, "image/png", "png");
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(0);
        FileOcrProcessor processor = processor(jdbcTemplate, new DisabledFileOcrEngine());

        assertThatThrownBy(() -> processor.extractImageText(3001L, 2001L, "user-uuid-2001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artifact state changed");
    }

    private void verifyArtifact(JdbcTemplate jdbcTemplate, String artifactType, String expectedSnippet) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> contentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_OCR),
                eq(artifactType),
                contentCaptor.capture(),
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
                .contains("fo.status in ('ENABLED', 'CLEAN')")
                .doesNotContain("sys_user");
        assertThat(contentCaptor.getValue()).asString().contains(expectedSnippet);
    }

    private void verifyLocationLookup(JdbcTemplate jdbcTemplate) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), Mockito.<RowMapper<?>>any(), eq(3001L));
        assertThat(sqlCaptor.getValue())
                .contains("where fo.id = ? and fo.deleted = 0 and fo.status in ('ENABLED', 'CLEAN')")
                .doesNotContain("sys_user");
    }
}
