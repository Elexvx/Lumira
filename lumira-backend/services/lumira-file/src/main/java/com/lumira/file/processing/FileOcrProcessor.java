package com.lumira.file.processing;

import com.lumira.file.config.UploadProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Lazy
@Service
public class FileOcrProcessor {

    public static final String ARTIFACT_OCR_RESULT = "OCR_RESULT";
    public static final String STATUS_EXTRACTED = "EXTRACTED";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_SKIPPED = "SKIPPED";
    private static final int MAX_STORED_CHARS = 200_000;

    private final JdbcTemplate jdbcTemplate;
    private final UploadProperties uploadProperties;
    private final FileOcrEngineSelector engineSelector;
    private final FileOwnerIdentityVerifier ownerIdentityVerifier;

    @Autowired
    public FileOcrProcessor(
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            FileOcrEngineSelector engineSelector,
            FileOwnerIdentityVerifier ownerIdentityVerifier
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
        this.engineSelector = engineSelector;
        this.ownerIdentityVerifier = ownerIdentityVerifier;
    }

    public FileOcrProcessor(
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            FileOcrEngineSelector engineSelector
    ) {
        this(jdbcTemplate, uploadProperties, engineSelector, null);
    }

    public OcrResult extractImageText(Long fileId, Long userId) {
        throw new IllegalStateException("File OCR owner UUID is required");
    }

    public OcrResult extractImageText(Long fileId, Long userId, String userUuid) {
        FileLocation location = findFileLocation(fileId);
        if (location == null) {
            throw new IllegalStateException("File object is unavailable for OCR: " + fileId);
        }
        Long ownerId = requireFileOwner(location, userId, userUuid);
        if (!"LOCAL".equalsIgnoreCase(location.storageType())) {
            throw new IllegalStateException("OCR only supports LOCAL storage: " + fileId);
        }
        if (!supports(location)) {
            throw new IllegalStateException("OCR is not implemented for file type: " + location.fileExtension());
        }
        Path source = resolveLocalPath(location.rootPath(), location.objectKey());
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("OCR source file is unavailable: " + source);
        }
        FileOcrEngine engine = engineSelector.select();
        OcrEngineResult engineResult = engine.extract(new FileOcrRequest(
                fileId,
                source,
                location.contentType(),
                normalize(location.fileExtension())
        ));
        String normalizedText = normalizeText(engineResult.text());
        String storedText = normalizedText.length() > MAX_STORED_CHARS
                ? normalizedText.substring(0, MAX_STORED_CHARS)
                : normalizedText;
        upsertOcrArtifact(fileId, engineResult, storedText, normalizedText.length(), ownerId, userUuid);
        if (StringUtils.hasText(storedText)) {
            upsertTextArtifact(fileId, storedText, ownerId, userUuid);
        }
        return new OcrResult(fileId, engineResult.engine(), engineResult.status(), storedText.length(), normalizedText.length() > storedText.length());
    }

    private FileLocation findFileLocation(Long fileId) {
        return jdbcTemplate.queryForObject(
                """
                        select fo.storage_type as storageType, fo.object_key as objectKey,
                               fo.content_type as contentType, fo.file_extension as fileExtension,
                               fo.uploaded_by as uploadedBy, fo.uploaded_by_uuid as uploadedByUserUuid,
                               coalesce(fs.root_path, '') as rootPath
                        from file_object fo
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

    private boolean supports(FileLocation location) {
        String mimeType = normalize(location.contentType());
        String extension = normalize(location.fileExtension());
        return mimeType.startsWith("image/")
                || List.of("png", "jpg", "jpeg", "webp", "bmp", "tif", "tiff").contains(extension);
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

    private void upsertOcrArtifact(Long fileId, OcrEngineResult result, String storedText, int sourceCharacters, Long userId, String userUuid) {
        String payload = buildPayload(result, storedText, sourceCharacters);
        upsertArtifact(fileId, FileProcessingTaskService.TASK_OCR, ARTIFACT_OCR_RESULT, payload, userId, userUuid);
    }

    private void upsertTextArtifact(Long fileId, String storedText, Long userId, String userUuid) {
        upsertArtifact(fileId, FileProcessingTaskService.TASK_OCR, FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT, storedText, userId, userUuid);
    }

    private void upsertArtifact(Long fileId, String taskType, String artifactType, String content, Long userId, String userUuid) {
        Long ownerId = requireUserId(userId);
        String ownerUuid = requireUserUuid(userUuid);
        int updated = jdbcTemplate.update(
                """
                        insert into file_processing_artifact (
                            file_id, task_type, artifact_type, content_text, content_length,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        )
                        select ?, ?, ?, ?, ?, ?, ?, ?, ?, 0
                        from file_object fo
                        where fo.id = ?
                          and fo.uploaded_by = ?
                          and fo.uploaded_by_uuid = ?
                          and fo.deleted = 0
                          and fo.status in ('ENABLED', 'CLEAN')
                        on duplicate key update
                            task_type = case when file_processing_artifact.created_by = values(created_by) and file_processing_artifact.created_by_uuid = values(created_by_uuid) then values(task_type) else file_processing_artifact.task_type end,
                            content_text = case when file_processing_artifact.created_by = values(created_by) and file_processing_artifact.created_by_uuid = values(created_by_uuid) then values(content_text) else file_processing_artifact.content_text end,
                            content_length = case when file_processing_artifact.created_by = values(created_by) and file_processing_artifact.created_by_uuid = values(created_by_uuid) then values(content_length) else file_processing_artifact.content_length end,
                            deleted = case when file_processing_artifact.created_by = values(created_by) and file_processing_artifact.created_by_uuid = values(created_by_uuid) then 0 else file_processing_artifact.deleted end,
                            updated_at = case when file_processing_artifact.created_by = values(created_by) and file_processing_artifact.created_by_uuid = values(created_by_uuid) then current_timestamp else file_processing_artifact.updated_at end,
                            updated_by = case when file_processing_artifact.created_by = values(created_by) and file_processing_artifact.created_by_uuid = values(created_by_uuid) then values(updated_by) else file_processing_artifact.updated_by end,
                            updated_by_uuid = case when file_processing_artifact.created_by = values(created_by) and file_processing_artifact.created_by_uuid = values(created_by_uuid) then values(updated_by_uuid) else file_processing_artifact.updated_by_uuid end
                        """,
                fileId,
                taskType,
                artifactType,
                content,
                content == null ? 0 : content.length(),
                ownerId,
                ownerUuid,
                ownerId,
                ownerUuid,
                fileId,
                ownerId,
                ownerUuid
        );
        if (updated <= 0) {
            throw new IllegalStateException("File OCR artifact state changed, please retry");
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
        if (ownerIdentityVerifier == null) {
            throw new IllegalStateException("File owner identity resolver is unavailable");
        }
        ownerIdentityVerifier.requireEnabledOwner(ownerId, location.uploadedByUserUuid());
        return ownerId;
    }

    private String buildPayload(OcrEngineResult result, String storedText, int sourceCharacters) {
        return "{"
                + "\"schemaVersion\":1,"
                + "\"engine\":\"" + escape(result.engine()) + "\","
                + "\"status\":\"" + escape(result.status()) + "\","
                + "\"reason\":\"" + escape(result.reason()) + "\","
                + "\"textCharacters\":" + storedText.length() + ","
                + "\"sourceCharacters\":" + sourceCharacters
                + "}";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "") : "";
    }

    private String normalizeText(String value) {
        return value == null ? "" : value
                .replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
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

    public record OcrResult(
            Long fileId,
            String engine,
            String status,
            int storedCharacters,
            boolean truncated
    ) {
    }
}
