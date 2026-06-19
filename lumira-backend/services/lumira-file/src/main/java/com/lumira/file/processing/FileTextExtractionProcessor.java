package com.lumira.file.processing;

import com.lumira.file.config.UploadProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FileTextExtractionProcessor {

    public static final String ARTIFACT_TEXT_CONTENT = "TEXT_CONTENT";
    private static final long MAX_DIRECT_TEXT_BYTES = 1024L * 1024L;
    private static final long MAX_DOCUMENT_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_STORED_CHARS = 200_000;

    private final JdbcTemplate jdbcTemplate;
    private final UploadProperties uploadProperties;
    private final Tika tika;

    @Autowired
    public FileTextExtractionProcessor(JdbcTemplate jdbcTemplate, UploadProperties uploadProperties) {
        this(jdbcTemplate, uploadProperties, new Tika());
    }

    FileTextExtractionProcessor(JdbcTemplate jdbcTemplate, UploadProperties uploadProperties, Tika tika) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
        this.tika = tika;
    }

    public TextExtractionResult extractText(Long tenantId, Long fileId, Long userId) {
        FileLocation location = findFileLocation(tenantId, fileId);
        if (location == null) {
            throw new IllegalStateException("File object is unavailable for text extraction: " + fileId);
        }
        if (!"LOCAL".equalsIgnoreCase(location.storageType())) {
            throw new IllegalStateException("Text extraction only supports LOCAL storage: " + fileId);
        }
        if (!supports(location)) {
            throw new IllegalStateException("Text extraction is not implemented for file type: " + location.fileExtension());
        }
        Path source = resolveLocalPath(location.rootPath(), location.objectKey());
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("Text source file is unavailable: " + source);
        }
        try {
            long size = Files.size(source);
            String content = extractContent(source, location, size);
            String normalizedContent = normalizeText(content);
            if (!StringUtils.hasText(normalizedContent)) {
                throw new IllegalStateException("File did not produce extractable text: " + fileId);
            }
            String storedContent = normalizedContent.length() > MAX_STORED_CHARS
                    ? normalizedContent.substring(0, MAX_STORED_CHARS)
                    : normalizedContent;
            upsertArtifact(tenantId, fileId, storedContent, userId);
            return new TextExtractionResult(source, storedContent.length(), normalizedContent.length() > storedContent.length());
        } catch (IOException | TikaException exception) {
            throw new IllegalStateException("Text extraction failed: " + source, exception);
        }
    }

    private String extractContent(Path source, FileLocation location, long size) throws IOException, TikaException {
        if (isPlainText(location)) {
            if (size > MAX_DIRECT_TEXT_BYTES) {
                throw new IllegalStateException("Text source file is too large for inline extraction: " + size);
            }
            return Files.readString(source, StandardCharsets.UTF_8);
        }
        if (size > MAX_DOCUMENT_BYTES) {
            throw new IllegalStateException("Document source file is too large for extraction: " + size);
        }
        try (InputStream inputStream = Files.newInputStream(source)) {
            return tika.parseToString(inputStream);
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

    private boolean supports(FileLocation location) {
        String mimeType = normalize(location.contentType());
        String extension = normalize(location.fileExtension());
        return isPlainText(mimeType, extension) || isDocument(mimeType, extension);
    }

    private boolean isPlainText(FileLocation location) {
        return isPlainText(normalize(location.contentType()), normalize(location.fileExtension()));
    }

    private boolean isPlainText(String mimeType, String extension) {
        return mimeType.startsWith("text/")
                || List.of("txt", "md", "markdown", "csv", "json", "log").contains(extension);
    }

    private boolean isDocument(String mimeType, String extension) {
        return mimeType.contains("pdf")
                || mimeType.contains("word")
                || mimeType.contains("excel")
                || mimeType.contains("powerpoint")
                || List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx").contains(extension);
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

    private void upsertArtifact(Long tenantId, Long fileId, String content, Long userId) {
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
                FileProcessingTaskService.TASK_TEXT_EXTRACT,
                ARTIFACT_TEXT_CONTENT,
                content,
                content == null ? 0 : content.length(),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "") : "";
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
    }

    private record FileLocation(
            String storageType,
            String objectKey,
            String rootPath,
            String contentType,
            String fileExtension
    ) {
    }

    public record TextExtractionResult(
            Path sourcePath,
            int storedCharacters,
            boolean truncated
    ) {
    }
}
