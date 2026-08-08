package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.file.config.UploadProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FileThumbnailProcessorTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    Path tempDir;

    @Test
    void generateThumbnail_shouldCreateBoundedJpegThumbnailForLocalImage() throws Exception {
        Path source = tempDir.resolve("2026/06/sample.jpg");
        Files.createDirectories(source.getParent());
        BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ImageIO.write(image, "jpg", source.toFile());

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("LOCAL");
                    when(resultSet.getString("objectKey")).thenReturn("2026/06/sample.jpg");
                    when(resultSet.getString("rootPath")).thenReturn(tempDir.toString());
                    when(resultSet.getString("contentType")).thenReturn("image/jpeg");
                    when(resultSet.getString("fileExtension")).thenReturn("jpg");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        FileOwnerIdentityVerifier ownerIdentityVerifier = mock(FileOwnerIdentityVerifier.class);
        var processor = processor(jdbcTemplate, uploadProperties, ownerIdentityVerifier);

        FileThumbnailProcessor.ThumbnailResult result = processor.generateThumbnail(3001L, 2001L, "user-uuid-2001");

        assertThat(result.status()).isEqualTo(FileThumbnailProcessor.STATUS_GENERATED);
        assertThat(result.thumbnailPath()).exists();
        BufferedImage thumbnail = ImageIO.read(result.thumbnailPath().toFile());
        assertThat(thumbnail.getWidth()).isEqualTo(320);
        assertThat(thumbnail.getHeight()).isEqualTo(160);
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, "\"status\":\"GENERATED\"");
        verify(ownerIdentityVerifier).requireEnabledOwner(2001L, "user-uuid-2001");
    }

    @Test
    void generateThumbnail_shouldStoreDeferredArtifactForRemoteImage() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("OSS");
                    when(resultSet.getString("objectKey")).thenReturn("remote/2026/06/sample.jpg");
                    when(resultSet.getString("rootPath")).thenReturn("");
                    when(resultSet.getString("contentType")).thenReturn("image/jpeg");
                    when(resultSet.getString("fileExtension")).thenReturn("jpg");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = processor(jdbcTemplate, uploadProperties);

        FileThumbnailProcessor.ThumbnailResult result = processor.generateThumbnail(3001L, 2001L, "user-uuid-2001");

        assertThat(result.status()).isEqualTo(FileThumbnailProcessor.STATUS_DEFERRED_REMOTE_STORAGE);
        assertThat(result.storageType()).isEqualTo("OSS");
        assertThat(result.objectKey()).isEqualTo("remote/2026/06/sample.jpg");
        verifyLocationLookup(jdbcTemplate);
        verifyArtifact(jdbcTemplate, "\"status\":\"DEFERRED_REMOTE_STORAGE\"");
    }

    @Test
    void generateThumbnail_shouldRejectImplicitOwner() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = processor(mock(JdbcTemplate.class), uploadProperties);

        assertThatThrownBy(() -> processor.generateThumbnail(3001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner is required");
    }

    @Test
    void generateThumbnail_shouldRejectMissingOwnerUuid() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        var processor = processor(mock(JdbcTemplate.class), uploadProperties);

        assertThatThrownBy(() -> processor.generateThumbnail(3001L, 2001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner UUID is required");
    }

    @Test
    void generateThumbnail_shouldRejectWhenArtifactWriteMissesTrustedSnapshot() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), Mockito.any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), Mockito.<RowMapper<?>>any(), eq(3001L)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString("storageType")).thenReturn("OSS");
                    when(resultSet.getString("objectKey")).thenReturn("remote/2026/06/sample.jpg");
                    when(resultSet.getString("rootPath")).thenReturn("");
                    when(resultSet.getString("contentType")).thenReturn("image/jpeg");
                    when(resultSet.getString("fileExtension")).thenReturn("jpg");
                    when(resultSet.getLong("uploadedBy")).thenReturn(2001L);
                    when(resultSet.getString("uploadedByUserUuid")).thenReturn("user-uuid-2001");
                    return mapper.mapRow(resultSet, 0);
                });
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setStorageRoot(tempDir.toString());
        FileOwnerIdentityVerifier ownerIdentityVerifier = mock(FileOwnerIdentityVerifier.class);
        var processor = processor(jdbcTemplate, uploadProperties, ownerIdentityVerifier);

        assertThatThrownBy(() -> processor.generateThumbnail(3001L, 2001L, "user-uuid-2001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artifact state changed");
    }

    private void verifyArtifact(JdbcTemplate jdbcTemplate, String expectedPayloadSnippet) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(3001L),
                eq(FileProcessingTaskService.TASK_THUMBNAIL),
                eq(FileThumbnailProcessor.ARTIFACT_THUMBNAIL_RESULT),
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
                .contains("fo.status in ('ENABLED', 'CLEAN')")
                .doesNotContain("sys_user");
        assertThat(payloadCaptor.getValue()).asString().contains(expectedPayloadSnippet);
    }

    private void verifyLocationLookup(JdbcTemplate jdbcTemplate) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), Mockito.<RowMapper<?>>any(), eq(3001L));
        assertThat(sqlCaptor.getValue())
                .contains("where fo.id = ? and fo.deleted = 0 and fo.status in ('ENABLED', 'CLEAN')")
                .doesNotContain("sys_user");
    }

    private FileThumbnailProcessor processor(JdbcTemplate jdbcTemplate, UploadProperties uploadProperties) {
        return processor(jdbcTemplate, uploadProperties, mock(FileOwnerIdentityVerifier.class));
    }

    private FileThumbnailProcessor processor(
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            FileOwnerIdentityVerifier ownerIdentityVerifier
    ) {
        return new FileThumbnailProcessor(jdbcTemplate, uploadProperties, ownerIdentityVerifier);
    }
}
