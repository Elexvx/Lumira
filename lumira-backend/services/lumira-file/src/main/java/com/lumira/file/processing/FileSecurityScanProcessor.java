package com.lumira.file.processing;

import com.lumira.file.config.UploadProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Lazy
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

    public SecurityScanResult scan(Long fileId, Long userId) {
        throw new IllegalStateException("File security scan owner UUID is required");
    }

    public SecurityScanResult scan(Long fileId, Long userId, String userUuid) {
        Instant startedAt = Instant.now();
        FileSecurityScanEngine engine = scanEngineSelector.select();
        try {
            FileLocation location = findFileLocation(fileId);
            if (location == null) {
                throw new IllegalStateException("File object is unavailable for security scan: " + fileId);
            }
            Long ownerId = requireFileOwner(location, userId, userUuid);
            SecurityScanResult result;
            if (!"LOCAL".equalsIgnoreCase(location.storageType())) {
                result = new SecurityScanResult(fileId, engine.engineName(), VERDICT_UNSUPPORTED_STORAGE, "REMOTE_STORAGE_NOT_SCANNED", 0L);
            } else {
                result = scanLocal(engine, location, fileId);
            }
            String ownerUuid = requireUserUuid(userUuid);
            upsertArtifact(fileId, result, ownerId, ownerUuid);
            if (VERDICT_THREAT_DETECTED.equals(result.verdict())) {
                quarantineFile(fileId, ownerId, ownerUuid);
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

    private FileLocation findFileLocation(Long fileId) {
        List<FileLocation> locations = jdbcTemplate.query(
                """
                        select fo.storage_type as storageType, fo.object_key as objectKey,
                               fo.file_extension as fileExtension, coalesce(fs.root_path, '') as rootPath,
                               fo.uploaded_by as uploadedBy, u.uuid as uploadedByUserUuid
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
                        where fo.id = ? and fo.deleted = 0 and fo.status = 'ENABLED'
                        limit 1
                        """,
                (rs, rowNum) -> new FileLocation(
                        rs.getString("storageType"),
                        rs.getString("objectKey"),
                        rs.getString("rootPath"),
                        rs.getString("fileExtension"),
                        rs.getLong("uploadedBy"),
                        rs.getString("uploadedByUserUuid")
                ),
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

    private void upsertArtifact(Long fileId, SecurityScanResult result, Long userId, String userUuid) {
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
                          and fo.status = 'ENABLED'
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
                FileProcessingTaskService.TASK_SECURITY_SCAN,
                ARTIFACT_SECURITY_SCAN_RESULT,
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
            throw new IllegalStateException("File security scan artifact state changed, please retry");
        }
    }

    private void quarantineFile(Long fileId, Long userId, String userUuid) {
        Long ownerId = requireUserId(userId);
        String ownerUuid = requireUserUuid(userUuid);
        int updated = jdbcTemplate.update(
                """
                        update file_object
                        set status = ?, updated_at = current_timestamp, updated_by = ?, updated_by_uuid = ?
                        where id = ?
                          and uploaded_by = ?
                          and uploaded_by_uuid = ?
                          and deleted = 0
                          and status = 'ENABLED'
                        """,
                "QUARANTINED",
                ownerId,
                ownerUuid,
                fileId,
                ownerId,
                ownerUuid
        );
        if (updated != 1) {
            throw new IllegalStateException("File security quarantine state changed, please retry");
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
            String fileExtension,
            Long uploadedBy,
            String uploadedByUserUuid
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
