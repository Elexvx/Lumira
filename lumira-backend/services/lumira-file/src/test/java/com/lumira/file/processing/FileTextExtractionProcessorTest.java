package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/notes.md");
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn("text/markdown");
                    when(resultSet.getString("fileExtension")).thenReturn("md");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = new FileTextExtractionProcessor(jdbcTemplate, uploadProperties);

        FileTextExtractionProcessor.TextExtractionResult result = processor.extractText(3001L, 2001L, "user-uuid-2001");

        assertThat(result.storedCharacters()).isEqualTo("# Notes\nhello Lumira".length());
        assertThat(result.truncated()).isFalse();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> contentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_TEXT_EXTRACT),
                eq(FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT),
                contentCaptor.capture(),
                eq("# Notes\nhello Lumira".length()),
                eq(2001L),
                eq("user-uuid-2001"),
                eq(2001L),
                eq("user-uuid-2001"),
                eq(3001L),
                eq(2001L),
                eq("user-uuid-2001")
        );
        verifyLocationLookup(jdbcTemplate);
        assertArtifactWriteRevalidatesFileOwner(sqlCaptor.getValue());
        assertThat(contentCaptor.getValue()).isEqualTo("# Notes\nhello Lumira");
    }

    @Test
    void extractText_shouldUseTikaForPdfAndStoreNormalizedArtifact() throws Exception {
        Path source = tempDir.resolve("2026/06/report.pdf");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "%PDF-1.4 placeholder", StandardCharsets.ISO_8859_1);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/report.pdf");
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn("application/pdf");
                    when(resultSet.getString("fileExtension")).thenReturn("pdf");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        Tika tika = mock(Tika.class);
        when(tika.parseToString(Mockito.any(java.io.InputStream.class))).thenReturn("PDF\tReport\n\n\nhello Lumira");
        var processor = new FileTextExtractionProcessor(jdbcTemplate, uploadProperties, tika);

        FileTextExtractionProcessor.TextExtractionResult result = processor.extractText(3001L, 2001L, "user-uuid-2001");

        assertThat(result.storedCharacters()).isEqualTo("PDF Report\n\nhello Lumira".length());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> contentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_TEXT_EXTRACT),
                eq(FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT),
                contentCaptor.capture(),
                eq("PDF Report\n\nhello Lumira".length()),
                eq(2001L),
                eq("user-uuid-2001"),
                eq(2001L),
                eq("user-uuid-2001"),
                eq(3001L),
                eq(2001L),
                eq("user-uuid-2001")
        );
        verifyLocationLookup(jdbcTemplate);
        assertArtifactWriteRevalidatesFileOwner(sqlCaptor.getValue());
        assertThat(contentCaptor.getValue()).isEqualTo("PDF Report\n\nhello Lumira");
    }

    @Test
    void extractText_shouldRejectWhenTaskOwnerDoesNotMatchFileOwner() throws Exception {
        Path source = tempDir.resolve("2026/06/notes.md");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "hello Lumira", StandardCharsets.UTF_8);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/notes.md");
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn("text/markdown");
                    when(resultSet.getString("fileExtension")).thenReturn("md");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = new FileTextExtractionProcessor(jdbcTemplate, uploadProperties);

        assertThatThrownBy(() -> processor.extractText(3001L, 9999L, "user-uuid-9999"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match file owner");
        verify(jdbcTemplate, never()).update(
                anyString(),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_TEXT_EXTRACT),
                eq(FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT),
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any(),
                Mockito.any()
        );
    }

    @Test
    void extractText_shouldRejectMissingOwnerUuid() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = new FileTextExtractionProcessor(mock(JdbcTemplate.class), uploadProperties);

        assertThatThrownBy(() -> processor.extractText(3001L, 2001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner UUID is required");
    }

    @Test
    void extractText_shouldRejectWhenArtifactWriteMissesTrustedSnapshot() throws Exception {
        Path source = tempDir.resolve("2026/06/notes.md");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "hello Lumira", StandardCharsets.UTF_8);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/notes.md");
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn("text/markdown");
                    when(resultSet.getString("fileExtension")).thenReturn("md");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = new FileTextExtractionProcessor(jdbcTemplate, uploadProperties);

        assertThatThrownBy(() -> processor.extractText(3001L, 2001L, "user-uuid-2001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artifact state changed");
    }

    private void assertArtifactWriteRevalidatesFileOwner(String sql) {
        assertThat(sql)
                .contains("from file_object fo")
                .contains("fo.uploaded_by_uuid = ?")
                .contains("fo.status = 'ENABLED'")
                .contains("u.status = 'ENABLED'");
    }

    private void verifyLocationLookup(JdbcTemplate jdbcTemplate) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), Mockito.<RowMapper<?>>any(), eq(3001L));
        assertThat(sqlCaptor.getValue())
                .contains("where fo.id = ? and fo.deleted = 0 and fo.status = 'ENABLED'")
                .contains("u.status = 'ENABLED'");
    }
}
