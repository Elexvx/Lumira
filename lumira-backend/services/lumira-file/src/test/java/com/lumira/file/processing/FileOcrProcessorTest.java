package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
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
        FileOcrProcessor processor = processor(jdbcTemplate, new DisabledFileOcrEngine());

        FileOcrProcessor.OcrResult result = processor.extractImageText(1001L, 3001L, 2001L);

        assertThat(result.engine()).isEqualTo(DisabledFileOcrEngine.ENGINE_NAME);
        assertThat(result.status()).isEqualTo(FileOcrProcessor.STATUS_SKIPPED);
        verifyArtifact(jdbcTemplate, FileOcrProcessor.ARTIFACT_OCR_RESULT, "\"status\":\"SKIPPED\"");
    }

    @Test
    void extractImageText_shouldStoreOcrResultAndTextContentWhenTextIsExtracted() throws Exception {
        Path source = writeImagePlaceholder("2026/06/invoice.jpg");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        mockLocation(jdbcTemplate, source, "image/jpeg", "jpg");
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

        FileOcrProcessor.OcrResult result = processor.extractImageText(1001L, 3001L, 2001L);

        assertThat(result.engine()).isEqualTo("FAKE_OCR");
        assertThat(result.status()).isEqualTo(FileOcrProcessor.STATUS_EXTRACTED);
        assertThat(result.storedCharacters()).isEqualTo("hello\n\nLumira OCR".length());
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
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        FileOcrEngineSelector selector = mock(FileOcrEngineSelector.class);
        when(selector.select()).thenReturn(engine);
        return new FileOcrProcessor(jdbcTemplate, uploadProperties, selector);
    }

    private void mockLocation(JdbcTemplate jdbcTemplate, Path source, String contentType, String extension) {
        String objectKey = tempDir.relativize(source).toString().replace('\\', '/');
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(1001L), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn(objectKey);
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn(contentType);
                    when(resultSet.getString("fileExtension")).thenReturn(extension);
                    return mapper.mapRow(resultSet, 0);
                });
    }

    private void verifyArtifact(JdbcTemplate jdbcTemplate, String artifactType, String expectedSnippet) {
        ArgumentCaptor<Object> contentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                anyString(),
                eq(1001L),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_OCR),
                eq(artifactType),
                contentCaptor.capture(),
                Mockito.anyInt(),
                eq(2001L),
                eq(2001L)
        );
        assertThat(contentCaptor.getValue()).asString().contains(expectedSnippet);
    }
}
