package com.lumira.file.processing;

import com.lumira.file.config.UploadProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public FileOcrProcessor(
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            FileOcrEngineSelector engineSelector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
        this.engineSelector = engineSelector;
    }

    public OcrResult extractImageText(Long tenantId, Long fileId, Long userId) {
        FileLocation location = findFileLocation(tenantId, fileId);
        if (location == null) {
            throw new IllegalStateException("File object is unavailable for OCR: " + fileId);
        }
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
        upsertOcrArtifact(tenantId, fileId, engineResult, storedText, normalizedText.length(), userId);
        if (StringUtils.hasText(storedText)) {
            upsertTextArtifact(tenantId, fileId, storedText, userId);
        }
        return new OcrResult(fileId, engineResult.engine(), engineResult.status(), storedText.length(), normalizedText.length() > storedText.length());
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

    private void upsertOcrArtifact(Long tenantId, Long fileId, OcrEngineResult result, String storedText, int sourceCharacters, Long userId) {
        String payload = buildPayload(result, storedText, sourceCharacters);
        upsertArtifact(tenantId, fileId, FileProcessingTaskService.TASK_OCR, ARTIFACT_OCR_RESULT, payload, userId);
    }

    private void upsertTextArtifact(Long tenantId, Long fileId, String storedText, Long userId) {
        upsertArtifact(tenantId, fileId, FileProcessingTaskService.TASK_OCR, FileTextExtractionProcessor.ARTIFACT_TEXT_CONTENT, storedText, userId);
    }

    private void upsertArtifact(Long tenantId, Long fileId, String taskType, String artifactType, String content, Long userId) {
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
                taskType,
                artifactType,
                content,
                content == null ? 0 : content.length(),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
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
            String fileExtension
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
