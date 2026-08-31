package com.lumira.saas.modules.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMigrationRequestEntity;
import com.lumira.saas.modules.plugin.runtime.PluginProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PluginMigrationService {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?is)\\b(DROP|TRUNCATE|RENAME|CHANGE(?:\\s+COLUMN)?|MODIFY(?:\\s+COLUMN)?|ALTER\\s+COLUMN)\\b");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?\\b");
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+`?[a-zA-Z0-9_]+`?\\s+ON\\s+`?([a-zA-Z0-9_]+)`?\\b");
    private static final Pattern ALTER_TABLE_ADD = Pattern.compile(
            "(?is)^\\s*ALTER\\s+TABLE\\s+`?([a-zA-Z0-9_]+)`?\\s+ADD\\s+(?:COLUMN\\s+|INDEX\\s+|KEY\\s+|CONSTRAINT\\s+).+");
    private static final Set<String> ROLLBACK_MODES = Set.of("APPLICATION_ONLY", "NOT_REQUIRED");

    private final PluginMigrationRequestService requestService;
    private final PluginProperties properties;
    private final ObjectMapper objectMapper;

    public PluginMigrationService(PluginMigrationRequestService requestService, PluginProperties properties, ObjectMapper objectMapper) {
        this.requestService = requestService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public MigrationDisposition requestExpandMigration(String pluginCode, String pluginVersion, Path versionHome,
                                                        String packageDigest, PluginDTO.PluginPackageMetadata metadata,
                                                        Long operatorId, String operatorUuid) {
        try {
            List<ResourceScript> scripts = resolveScripts(versionHome);
            if (scripts.isEmpty()) return MigrationDisposition.NO_MIGRATION;
            MigrationMetadata migration = requireMetadata(metadata);
            String namespace = tableNamespace(pluginCode);
            List<Map<String, String>> payload = new ArrayList<>();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, pluginCode, pluginVersion, packageDigest, migration.schemaVersion(),
                    migration.phase(), migration.rollbackMode(), String.join(",", migration.compatibleReaders()), namespace);
            for (ResourceScript script : scripts) {
                String sql = read(script.resource());
                validateExpandSql(sql, namespace);
                updateDigest(digest, script.stepName(), sql);
                Map<String, String> item = new LinkedHashMap<>();
                item.put("stepName", script.stepName());
                item.put("scriptPath", script.scriptPath());
                item.put("sql", sql);
                payload.add(item);
            }
            String migrationDigest = HexFormat.of().formatHex(digest.digest());
            PluginMigrationRequestEntity existing = requestService.find(pluginCode, pluginVersion, migrationDigest).orElse(null);
            if (existing != null) {
                if ("SUCCEEDED".equals(existing.getRequestStatus())) return MigrationDisposition.MIGRATED;
                if ("FAILED".equals(existing.getRequestStatus())) {
                    String detail = StringUtils.hasText(existing.getFailureReason()) ? existing.getFailureReason() : "unknown failure";
                    throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR,
                            "Recorded plugin migration failed: " + detail + "; recovery: " + existing.getRecoveryAction());
                }
                return MigrationDisposition.MIGRATION_PENDING;
            }
            PluginMigrationRequestEntity request = new PluginMigrationRequestEntity();
            request.setPluginCode(pluginCode);
            request.setPluginVersion(pluginVersion);
            request.setSchemaVersion(migration.schemaVersion());
            request.setPhase("EXPAND");
            request.setRollbackMode(migration.rollbackMode());
            request.setCompatibleReaders(String.join(",", migration.compatibleReaders()));
            request.setTableNamespace(namespace);
            request.setPackageDigest(requireDigest(packageDigest, "packageDigest"));
            request.setMigrationDigest(migrationDigest);
            request.setReleaseId(requireText(properties.getReleaseId(), "releaseId"));
            request.setRequestStatus("PENDING_APPROVAL");
            request.setLifecycleStatus("MIGRATION_PENDING");
            request.setScriptPayload(objectMapper.writeValueAsString(payload));
            request.setRecoveryAction("Review failure, reconcile recorded statements, then create a new fenced expand request");
            request.setCreatedBy(operatorId);
            request.setCreatedByUuid(requireText(operatorUuid, "operatorUuid"));
            requestService.enqueue(request);
            return MigrationDisposition.MIGRATION_PENDING;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR,
                    "Plugin migration request could not be persisted: " + rootCauseMessage(exception));
        }
    }

    /** Application runtimes never execute or enqueue destructive down migrations. */
    public void executeDownMigrations(String pluginCode, String pluginVersion, Path versionHome,
                                      Long operatorId, String operatorUuid) {
        throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR,
                "Plugin down migrations are forbidden; use application rollback or an approved recovery release");
    }

    static void validateExpandSql(String sql, String namespace) {
        String source = stripComments(sql);
        if (FORBIDDEN.matcher(source).find()) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "Plugin migration contains destructive SQL");
        }
        for (String raw : source.split(";")) {
            String statement = raw.trim();
            if (statement.isEmpty()) continue;
            String table = tableForAllowedStatement(statement);
            if (table == null) {
                throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID,
                        "Only CREATE TABLE, CREATE INDEX and ALTER TABLE ADD are allowed in plugin expand migrations");
            }
            if (!table.toLowerCase(Locale.ROOT).startsWith(namespace)) {
                throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID,
                        "Plugin migration table must use namespace " + namespace + "*");
            }
        }
    }

    static String tableNamespace(String pluginCode) {
        String normalized = requireText(pluginCode, "pluginCode").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "pluginCode cannot produce a table namespace");
        }
        return "plugin_" + normalized + "_";
    }

    private MigrationMetadata requireMetadata(PluginDTO.PluginPackageMetadata metadata) {
        if (metadata == null) throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "Plugin migration metadata is required");
        String schemaVersion = requireText(metadata.getMigrationSchemaVersion(), "migration.schemaVersion");
        String phase = requireText(metadata.getMigrationPhase(), "migration.phase").toUpperCase(Locale.ROOT);
        if (!"EXPAND".equals(phase)) throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "Only EXPAND plugin migrations are allowed");
        String rollbackMode = requireText(metadata.getRollbackMode(), "migration.rollbackMode").toUpperCase(Locale.ROOT);
        if (!ROLLBACK_MODES.contains(rollbackMode)) {
            throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "rollbackMode must be APPLICATION_ONLY or NOT_REQUIRED");
        }
        List<String> readers = metadata.getCompatibleReaders() == null ? List.of() : metadata.getCompatibleReaders().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (readers.isEmpty()) throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, "migration.compatibleReaders is required");
        return new MigrationMetadata(schemaVersion, phase, rollbackMode, readers);
    }

    private List<ResourceScript> resolveScripts(Path versionHome) throws IOException {
        if (versionHome == null) return List.of();
        Path migrationsDir = versionHome.resolve("migrations").resolve("up");
        if (!Files.isDirectory(migrationsDir)) return List.of();
        try (var paths = Files.list(migrationsDir)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> new ResourceScript(path.getFileName().toString(), path.toString(), new FileSystemResource(path)))
                    .toList();
        }
    }

    private static String tableForAllowedStatement(String statement) {
        for (Pattern pattern : List.of(CREATE_TABLE, CREATE_INDEX, ALTER_TABLE_ADD)) {
            Matcher matcher = pattern.matcher(statement);
            if (matcher.find()) return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?m)--.*$", "").replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static String read(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void updateDigest(MessageDigest digest, String... values) {
        for (String value : values) {
            digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
    }

    private static String requireDigest(String value, String name) {
        String digest = requireText(value, name).toLowerCase(Locale.ROOT);
        if (!digest.matches("[a-f0-9]{64}")) throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, name + " must be a SHA-256 digest");
        return digest;
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) throw new BizException(ErrorCode.PLUGIN_PACKAGE_INVALID, name + " is required");
        return value.trim();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        return StringUtils.hasText(cursor.getMessage()) ? cursor.getMessage() : throwable.getClass().getSimpleName();
    }

    public enum MigrationDisposition { NO_MIGRATION, MIGRATION_PENDING, MIGRATED }
    private record MigrationMetadata(String schemaVersion, String phase, String rollbackMode, List<String> compatibleReaders) { }
    private record ResourceScript(String stepName, String scriptPath, Resource resource) { }
}
