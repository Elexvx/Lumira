package com.lumira.file.processing;

import com.lumira.file.config.UploadProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FileSecurityScanProcessor {

    public static final String ARTIFACT_SECURITY_SCAN_RESULT = "SECURITY_SCAN_RESULT";
    public static final String VERDICT_CLEAN = "CLEAN";
    public static final String VERDICT_THREAT_DETECTED = "THREAT_DETECTED";
    public static final String VERDICT_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    public static final String VERDICT_UNSUPPORTED_STORAGE = "UNSUPPORTED_STORAGE";
    public static final String ENGINE_NAME = "LUMIRA_INLINE_RULES";

    private final JdbcTemplate jdbcTemplate;
    private final UploadProperties uploadProperties;
    private final FileSecurityScanMetrics securityScanMetrics;
    private final FileSecurityScanEngineSelector scanEngineSelector;

    public FileSecurityScanProcessor(
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            FileSecurityScanMetrics securityScanMetrics,
            FileSecurityScanEngineSelector scanEngineSelector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
        this.securityScanMetrics = securityScanMetrics;
        this.scanEngineSelector = scanEngineSelector;
    }

    public SecurityScanResult scan(Long tenantId, Long fileId, Long userId) {
        Instant startedAt = Instant.now();
        FileSecurityScanEngine engine = scanEngineSelector.select();
        try {
            FileLocation location = findFileLocation(tenantId, fileId);
            if (location == null) {
                throw new IllegalStateException("File object is unavailable for security scan: " + fileId);
            }
            SecurityScanResult result;
            if (!"LOCAL".equalsIgnoreCase(location.storageType())) {
                result = new SecurityScanResult(fileId, engine.engineName(), VERDICT_UNSUPPORTED_STORAGE, "REMOTE_STORAGE_NOT_SCANNED", 0L);
            } else {
                result = scanLocal(engine, location, fileId);
            }
            upsertArtifact(tenantId, fileId, result, userId);
            if (VERDICT_THREAT_DETECTED.equals(result.verdict())) {
                quarantineFile(tenantId, fileId, userId);
            }
            securityScanMetrics.recordVerdict(result.engine(), result.verdict(), Duration.between(startedAt, Instant.now()));
            return result;
        } catch (RuntimeException exception) {
            securityScanMetrics.recordFailure(engine.engineName(), exception, Duration.between(startedAt, Instant.now()));
            throw exception;
        }
    }

    private SecurityScanResult scanLocal(FileSecurityScanEngine engine, FileLocation location, Long fileId) {
        Path source = resolveLocalPath(location.rootPath(), location.objectKey());
        SecurityScanEngineResult scanResult = engine.scan(new FileSecurityScanRequest(fileId, source, normalize(location.fileExtension())));
        if (VERDICT_CLEAN.equals(scanResult.verdict()) && isReviewRequired(location)) {
            return new SecurityScanResult(fileId, scanResult.engine(), VERDICT_REVIEW_REQUIRED, "HIGH_RISK_EXTENSION", scanResult.scannedBytes());
        }
        return new SecurityScanResult(fileId, scanResult.engine(), scanResult.verdict(), scanResult.reason(), scanResult.scannedBytes());
    }

    private FileLocation findFileLocation(Long tenantId, Long fileId) {
        List<FileLocation> locations = jdbcTemplate.query(
                """
                        select fo.storage_type as storageType, fo.object_key as objectKey,
                               fo.file_extension as fileExtension, coalesce(fs.root_path, '') as rootPath
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
                        rs.getString("fileExtension")
                ),
                tenantId,
                fileId
        );
        return locations.isEmpty() ? null : locations.getFirst();
    }

    private boolean isReviewRequired(FileLocation location) {
        String extension = normalize(location.fileExtension());
        return List.of("bat", "cmd", "com", "dll", "dmg", "exe", "jar", "js", "msi", "ps1", "scr", "sh", "vbs")
                .contains(extension);
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

    private void upsertArtifact(Long tenantId, Long fileId, SecurityScanResult result, Long userId) {
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
                FileProcessingTaskService.TASK_SECURITY_SCAN,
                ARTIFACT_SECURITY_SCAN_RESULT,
                payload,
                payload.length(),
                userId == null ? 0L : userId,
                userId == null ? 0L : userId
        );
    }

    private void quarantineFile(Long tenantId, Long fileId, Long userId) {
        jdbcTemplate.update(
                """
                        update file_object
                        set status = ?, updated_at = current_timestamp, updated_by = ?
                        where tenant_id = ? and id = ? and deleted = 0
                        """,
                "QUARANTINED",
                userId == null ? 0L : userId,
                tenantId,
                fileId
        );
    }

    private String buildPayload(SecurityScanResult result) {
        return "{"
                + "\"schemaVersion\":1,"
                + "\"engine\":\"" + escape(result.engine()) + "\","
                + "\"verdict\":\"" + escape(result.verdict()) + "\","
                + "\"reason\":\"" + escape(result.reason()) + "\","
                + "\"scannedBytes\":" + result.scannedBytes()
                + "}";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "") : "";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record FileLocation(
            String storageType,
            String objectKey,
            String rootPath,
            String fileExtension
    ) {
    }

    public record SecurityScanResult(
            Long fileId,
            String engine,
            String verdict,
            String reason,
            long scannedBytes
    ) {
    }
}
