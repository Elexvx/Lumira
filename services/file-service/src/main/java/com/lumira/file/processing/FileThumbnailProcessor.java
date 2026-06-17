package com.lumira.file.processing;

import com.lumira.file.config.UploadProperties;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FileThumbnailProcessor {

    public static final String ARTIFACT_THUMBNAIL_RESULT = "THUMBNAIL_RESULT";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_DEFERRED_REMOTE_STORAGE = "DEFERRED_REMOTE_STORAGE";
    private static final int MAX_SIZE = 320;

    private final JdbcTemplate jdbcTemplate;
    private final UploadProperties uploadProperties;

    public FileThumbnailProcessor(JdbcTemplate jdbcTemplate, UploadProperties uploadProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
    }

    public ThumbnailResult generateThumbnail(Long tenantId, Long fileId) {
        return generateThumbnail(tenantId, fileId, null);
    }

    public ThumbnailResult generateThumbnail(Long tenantId, Long fileId, Long userId) {
        FileLocation location = findFileLocation(tenantId, fileId);
        if (location == null) {
            throw new IllegalStateException("File object is unavailable for thumbnail generation: " + fileId);
        }
        if (!"LOCAL".equalsIgnoreCase(location.storageType())) {
            ThumbnailResult result = ThumbnailResult.deferred(fileId, location.storageType(), location.objectKey());
            upsertArtifact(tenantId, fileId, result, userId);
            return result;
        }
        Path source = resolveLocalPath(location.rootPath(), location.objectKey());
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("Image source file is unavailable: " + source);
        }
        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) {
                throw new IllegalStateException("Image source file is unreadable: " + source);
            }
            Path thumbnail = thumbnailPath(source);
            Files.createDirectories(thumbnail.getParent());
            BufferedImage scaled = scale(original);
            ImageIO.write(scaled, "jpg", thumbnail.toFile());
            ThumbnailResult result = ThumbnailResult.generated(source, thumbnail, original.getWidth(), original.getHeight(), scaled.getWidth(), scaled.getHeight());
            upsertArtifact(tenantId, fileId, result, userId);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Thumbnail generation failed: " + source, exception);
        }
    }

    private FileLocation findFileLocation(Long tenantId, Long fileId) {
        return jdbcTemplate.queryForObject(
                """
                        select fo.storage_type as storageType, fo.object_key as objectKey,
                               fo.content_type as contentType, fo.file_extension as fileExtension,
                               coalesce(fs.root_path, '') as rootPath
                        from file_object fo
                        left join file_storage_space fs
                          on fs.tenant_id = fo.tenant_id
                         and fs.storage_key = fo.bucket
                         and fs.deleted = 0
                        where fo.tenant_id = ? and fo.id = ? and fo.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new FileLocation(
                        rs.getString("storageType"),
                        rs.getString("objectKey"),
                        rs.getString("rootPath"),
                        rs.getString("contentType"),
                        rs.getString("fileExtension")
                ),
                tenantId,
                fileId
        );
    }

    private Path resolveLocalPath(String rootPath, String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalStateException("File object key is empty");
        }
        Path root = resolveStorageRoot(rootPath);
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("File object key escapes storage root");
        }
        return target;
    }

    private Path resolveStorageRoot(String rootPath) {
        if (!StringUtils.hasText(rootPath)) {
            return Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        }
        Path root = Path.of(rootPath);
        if (root.isAbsolute()) {
            return root.normalize();
        }
        Path uploadRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        String normalizedRootPath = rootPath.trim().replace('\\', '/');
        while (normalizedRootPath.endsWith("/")) {
            normalizedRootPath = normalizedRootPath.substring(0, normalizedRootPath.length() - 1);
        }
        if ("storage/uploads".equals(normalizedRootPath)) {
            return uploadRoot;
        }
        if (normalizedRootPath.startsWith("storage/uploads/")) {
            return uploadRoot.resolve(normalizedRootPath.substring("storage/uploads/".length())).normalize();
        }
        return uploadRoot.resolve(normalizedRootPath).normalize();
    }

    private Path thumbnailPath(Path source) {
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot <= 0 ? fileName : fileName.substring(0, dot);
        Path parent = source.getParent() == null ? source.toAbsolutePath().getParent() : source.getParent();
        return parent.resolve(baseName + ".thumb.jpg");
    }

    private BufferedImage scale(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        double ratio = Math.min(1.0, Math.min((double) MAX_SIZE / width, (double) MAX_SIZE / height));
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private void upsertArtifact(Long tenantId, Long fileId, ThumbnailResult result, Long userId) {
        String payload = buildPayload(result);
        jdbcTemplate.update(
                """
                        insert into file_processing_artifact (
                            tenant_id, file_id, task_type, artifact_type, content_text, content_length,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            task_type = values(task_type),
                            content_text = values(content_text),
                            content_length = values(content_length),
                            deleted = 0,
                            updated_at = current_timestamp,
                            updated_by = values(updated_by)
                        """,
                tenantId,
                fileId,
                FileProcessingTaskService.TASK_THUMBNAIL,
                ARTIFACT_THUMBNAIL_RESULT,
                payload,
                payload.length(),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
    }

    private String buildPayload(ThumbnailResult result) {
        return "{"
                + "\"schemaVersion\":1,"
                + "\"status\":\"" + escape(result.status()) + "\","
                + "\"storageType\":\"" + escape(result.storageType()) + "\","
                + "\"sourcePath\":\"" + escape(pathToString(result.sourcePath())) + "\","
                + "\"thumbnailPath\":\"" + escape(pathToString(result.thumbnailPath())) + "\","
                + "\"objectKey\":\"" + escape(result.objectKey()) + "\","
                + "\"sourceWidth\":" + result.sourceWidth() + ","
                + "\"sourceHeight\":" + result.sourceHeight() + ","
                + "\"thumbnailWidth\":" + result.thumbnailWidth() + ","
                + "\"thumbnailHeight\":" + result.thumbnailHeight()
                + "}";
    }

    private String pathToString(Path path) {
        return path == null ? "" : path.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record FileLocation(
            String storageType,
            String objectKey,
            String rootPath,
            String contentType,
            String fileExtension
    ) {
    }

    public record ThumbnailResult(
            String status,
            String storageType,
            Path sourcePath,
            Path thumbnailPath,
            String objectKey,
            int sourceWidth,
            int sourceHeight,
            int thumbnailWidth,
            int thumbnailHeight
    ) {
        static ThumbnailResult generated(
                Path sourcePath,
                Path thumbnailPath,
                int sourceWidth,
                int sourceHeight,
                int thumbnailWidth,
                int thumbnailHeight
        ) {
            return new ThumbnailResult(
                    STATUS_GENERATED,
                    "LOCAL",
                    sourcePath,
                    thumbnailPath,
                    "",
                    sourceWidth,
                    sourceHeight,
                    thumbnailWidth,
                    thumbnailHeight
            );
        }

        static ThumbnailResult deferred(Long fileId, String storageType, String objectKey) {
            return new ThumbnailResult(
                    STATUS_DEFERRED_REMOTE_STORAGE,
                    storageType,
                    null,
                    null,
                    objectKey == null ? "" : objectKey,
                    0,
                    0,
                    0,
                    0
            );
        }
    }
}
