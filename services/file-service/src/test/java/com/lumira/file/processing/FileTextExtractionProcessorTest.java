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
import org.apache.tika.Tika;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FileTextExtractionProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractText_shouldStoreTextArtifactForLocalMarkdownFile() throws Exception {
        Path source = tempDir.resolve("2026/06/notes.md");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "# Notes\nhello Lumira", StandardCharsets.UTF_8);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(1001L), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/notes.md");
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn("text/markdown");
                    when(resultSet.getString("fileExtension")).thenReturn("md");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = new FileTextExtractionProcessor(jdbcTemplate, uploadProperties);

        FileTextExtractionProcessor.TextExtractionResult result = processor.extractText(1001L, 3001L, 2001L);

        assertThat(result.storedCharacters()).isEqualTo("# Notes\nhello Lumira".length());
        assertThat(result.truncated()).isFalse();
        ArgumentCaptor<Object> contentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                anyString(),
                eq(1001L),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_TEXT_EXTRACT),
                eq(FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT),
                contentCaptor.capture(),
                eq("# Notes\nhello Lumira".length()),
                eq(2001L),
                eq(2001L)
        );
        assertThat(contentCaptor.getValue()).isEqualTo("# Notes\nhello Lumira");
    }

    @Test
    void extractText_shouldUseTikaForPdfAndStoreNormalizedArtifact() throws Exception {
        Path source = tempDir.resolve("2026/06/report.pdf");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "%PDF-1.4 placeholder", StandardCharsets.ISO_8859_1);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(1001L), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/report.pdf");
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn("application/pdf");
                    when(resultSet.getString("fileExtension")).thenReturn("pdf");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        Tika tika = mock(Tika.class);
        when(tika.parseToString(Mockito.any(java.io.InputStream.class))).thenReturn("PDF\tReport\n\n\nhello Lumira");
        var processor = new FileTextExtractionProcessor(jdbcTemplate, uploadProperties, tika);

        FileTextExtractionProcessor.TextExtractionResult result = processor.extractText(1001L, 3001L, 2001L);

        assertThat(result.storedCharacters()).isEqualTo("PDF Report\n\nhello Lumira".length());
        ArgumentCaptor<Object> contentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                anyString(),
                eq(1001L),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_TEXT_EXTRACT),
                eq(FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT),
                contentCaptor.capture(),
                eq("PDF Report\n\nhello Lumira".length()),
                eq(2001L),
                eq(2001L)
        );
        assertThat(contentCaptor.getValue()).isEqualTo("PDF Report\n\nhello Lumira");
    }
}
