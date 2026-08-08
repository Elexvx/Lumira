package com.lumira.file.processing;

import com.lumira.file.config.UploadProperties;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Lazy
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

    public ThumbnailResult generateThumbnail(Long fileId) {
        throw new IllegalStateException("File thumbnail owner is required");
    }

    public ThumbnailResult generateThumbnail(Long fileId, Long userId) {
        throw new IllegalStateException("File thumbnail owner UUID is required");
    }

    public ThumbnailResult generateThumbnail(Long fileId, Long userId, String userUuid) {
        FileLocation location = findFileLocation(fileId);
        if (location == null) {
            throw new IllegalStateException("File object is unavailable for thumbnail generation: " + fileId);
        }
        Long ownerId = requireFileOwner(location, userId, userUuid);
        if (!"LOCAL".equalsIgnoreCase(location.storageType())) {
            ThumbnailResult result = ThumbnailResult.deferred(fileId, location.storageType(), location.objectKey());
            upsertArtifact(fileId, result, ownerId, userUuid);
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
            upsertArtifact(fileId, result, ownerId, userUuid);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Thumbnail generation failed: " + source, exception);
        }
    }

    private FileLocation findFileLocation(Long fileId) {
        return jdbcTemplate.queryForObject(
                """
                        select fo.storage_type as storageType, fo.object_key as objectKey,
                               fo.content_type as contentType, fo.file_extension as fileExtension,
                               fo.uploaded_by as uploadedBy, u.uuid as uploadedByUserUuid,
                               coalesce(fs.root_path, '') as rootPath
                        from file_object fo
                        join sys_user u
                          on u.id = fo.uploaded_by
                         and u.uuid = fo.uploaded_by_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                         and u.uuid is not null
                         and u.uuid <> ''
                        left join file_storage_space fs
                          on fs.storage_key = fo.bucket
                         and fs.deleted = 0
                        where fo.id = ? and fo.deleted = 0 and fo.status in ('ENABLED', 'CLEAN')
                        limit 1
                        """,
                (rs, rowNum) -> new FileLocation(
                        rs.getString("storageType"),
                        rs.getString("objectKey"),
                        rs.getString("rootPath"),
                        rs.getString("contentType"),
                        rs.getString("fileExtension"),
                        rs.getLong("uploadedBy"),
                        rs.getString("uploadedByUserUuid")
                ),
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

    private void upsertArtifact(Long fileId, ThumbnailResult result, Long userId, String userUuid) {
        Long ownerId = requireUserId(userId);
        String ownerUuid = requireUserUuid(userUuid);
        String payload = buildPayload(result);
        int updated = jdbcTemplate.update(
                """
                        insert into file_processing_artifact (
                            file_id, task_type, artifact_type, content_text, content_length,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        )
                        select ?, ?, ?, ?, ?, ?, ?, ?, ?, 0
                        from file_object fo
                        join sys_user u
                          on u.id = fo.uploaded_by
                         and u.uuid = fo.uploaded_by_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                        where fo.id = ?
                          and fo.uploaded_by = ?
                          and fo.uploaded_by_uuid = ?
                          and fo.deleted = 0
                          and fo.status in ('ENABLED', 'CLEAN')
                        on duplicate key update
                            task_type = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(task_type) else task_type end,
                            content_text = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(content_text) else content_text end,
                            content_length = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(content_length) else content_length end,
                            deleted = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then 0 else deleted end,
                            updated_at = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then current_timestamp else updated_at end,
                            updated_by = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(updated_by) else updated_by end,
                            updated_by_uuid = case when created_by = values(created_by) and created_by_uuid = values(created_by_uuid) then values(updated_by_uuid) else updated_by_uuid end
                        """,
                fileId,
                FileProcessingTaskService.TASK_THUMBNAIL,
                ARTIFACT_THUMBNAIL_RESULT,
                payload,
                payload.length(),
                ownerId,
                ownerUuid,
                ownerId,
                ownerUuid,
                fileId,
                ownerId,
                ownerUuid
        );
        if (updated <= 0) {
            throw new IllegalStateException("File thumbnail artifact state changed, please retry");
        }
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("File processing artifact owner is required");
        }
        return userId;
    }

    private String requireUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new IllegalStateException("File processing artifact owner UUID is required");
        }
        return userUuid.trim();
    }

    private Long requireFileOwner(FileLocation location, Long userId, String userUuid) {
        Long ownerId = requireUserId(location.uploadedBy());
        Long requestedOwnerId = requireUserId(userId);
        if (!ownerId.equals(requestedOwnerId)
                || !StringUtils.hasText(location.uploadedByUserUuid())
                || !StringUtils.hasText(userUuid)
                || !location.uploadedByUserUuid().trim().equals(userUuid.trim())) {
            throw new IllegalStateException("File processing task owner does not match file owner");
        }
        return ownerId;
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
            String fileExtension,
            Long uploadedBy,
            String uploadedByUserUuid
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
