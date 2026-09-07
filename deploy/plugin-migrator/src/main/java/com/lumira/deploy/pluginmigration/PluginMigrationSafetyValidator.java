package com.lumira.deploy.pluginmigration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PluginMigrationSafetyValidator {
    private static final int MAX_PAYLOAD_CHARS = 8 * 1024 * 1024;
    private static final int MAX_SCRIPT_CHARS = 1024 * 1024;
    private static final int MAX_SCRIPTS = 100;
    private static final Pattern DIGEST = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern STEP_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?is)\\b(DROP|TRUNCATE|RENAME|CHANGE(?:\\s+COLUMN)?|MODIFY(?:\\s+COLUMN)?|ALTER\\s+COLUMN|"
                    + "INSERT|UPDATE|DELETE|SELECT|CALL|DO|LOAD|GRANT|REVOKE|LOCK|UNLOCK|USE|HANDLER|REPLACE)\\b");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?\\s*\\(.+\\)\\s*$");
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+`?[a-zA-Z0-9_]+`?\\s+ON\\s+`?([a-zA-Z0-9_]+)`?\\s*\\(.+\\)\\s*$");
    private static final Pattern ALTER_TABLE_ADD = Pattern.compile(
            "(?is)^\\s*ALTER\\s+TABLE\\s+`?([a-zA-Z0-9_]+)`?\\s+ADD\\s+(?:COLUMN\\s+|INDEX\\s+|KEY\\s+|CONSTRAINT\\s+).+$");
    private static final Pattern TABLE_REFERENCE = Pattern.compile(
            "(?is)\\bREFERENCES\\s+`?([a-zA-Z0-9_]+)`?\\b");

    private final ObjectMapper objectMapper;

    PluginMigrationSafetyValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    PluginMigrationRequest.Validated validate(PluginMigrationRequest request, String requiredReleaseId) {
        require(request != null, "migration request is required");
        require("APPROVED".equals(request.requestStatus()) || "RUNNING".equals(request.requestStatus())
                        || "RECOVERING".equals(request.requestStatus()) || "PENDING_APPROVAL".equals(request.requestStatus()),
                "migration request status cannot be validated");
        require("MIGRATION_PENDING".equals(request.lifecycleStatus()), "plugin lifecycle is not MIGRATION_PENDING");
        require("EXPAND".equals(request.phase()), "only EXPAND plugin migrations are allowed");
        require(Set.of("APPLICATION_ONLY", "NOT_REQUIRED").contains(request.rollbackMode()),
                "rollback mode is not expand-safe");
        require(hasText(request.schemaVersion()), "schema version is required");
        require(hasText(request.compatibleReaders()), "compatible readers are required");
        require(hasText(requiredReleaseId) && requiredReleaseId.equals(request.releaseId()), "release fence does not match");
        require(request.operationEpoch() > 0, "operation epoch must be positive");
        require(DIGEST.matcher(normalized(request.packageDigest())).matches(), "package digest is invalid");
        require(DIGEST.matcher(normalized(request.migrationDigest())).matches(), "migration digest is invalid");
        if (hasText(request.expectedSchemaDigest())) {
            require(DIGEST.matcher(normalized(request.expectedSchemaDigest())).matches(), "expected schema digest is invalid");
        }

        String expectedNamespace = namespace(request.pluginCode());
        require(expectedNamespace.equals(request.tableNamespace()), "plugin table namespace does not match plugin code");
        List<PluginMigrationRequest.Script> scripts = parseScripts(request.scriptPayload());
        List<String> statements = new ArrayList<>();
        Set<String> targetTables = new HashSet<>();
        String previousStep = null;
        Set<String> uniqueSteps = new HashSet<>();
        for (PluginMigrationRequest.Script script : scripts) {
            require(STEP_NAME.matcher(script.stepName()).matches(), "migration step name is invalid");
            require(uniqueSteps.add(script.stepName()), "migration step names must be unique");
            require(previousStep == null || previousStep.compareTo(script.stepName()) < 0,
                    "migration steps are not in deterministic order");
            previousStep = script.stepName();
            require(script.sql() != null && script.sql().length() <= MAX_SCRIPT_CHARS, "migration script is too large");
            SqlValidation validatedSql = validateSql(script.sql(), expectedNamespace);
            statements.addAll(validatedSql.statements());
            targetTables.addAll(validatedSql.targetTables());
        }
        require(!statements.isEmpty(), "migration payload contains no executable statements");
        require(constantTimeEquals(request.migrationDigest(), digest(request, scripts)), "migration digest does not match payload");
        return new PluginMigrationRequest.Validated(List.copyOf(scripts), List.copyOf(statements),
                targetTables.stream().sorted().toList());
    }

    private List<PluginMigrationRequest.Script> parseScripts(String payload) {
        require(payload != null && payload.length() <= MAX_PAYLOAD_CHARS, "migration payload is missing or too large");
        try {
            List<Map<String, String>> values = objectMapper.readValue(payload, new TypeReference<>() { });
            require(values != null && !values.isEmpty() && values.size() <= MAX_SCRIPTS,
                    "migration payload script count is invalid");
            List<PluginMigrationRequest.Script> scripts = new ArrayList<>();
            for (Map<String, String> value : values) {
                require(value != null && value.size() == 3
                                && value.containsKey("stepName") && value.containsKey("scriptPath") && value.containsKey("sql"),
                        "migration payload contains unsupported fields");
                scripts.add(new PluginMigrationRequest.Script(value.get("stepName"), value.get("scriptPath"), value.get("sql")));
            }
            return List.copyOf(scripts);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("migration payload is not valid JSON", exception);
        }
    }

    private SqlValidation validateSql(String sql, String namespace) {
        String source = stripComments(sql);
        require(!FORBIDDEN.matcher(source).find(), "migration contains destructive or non-DDL SQL");
        List<String> statements = splitStatements(source);
        Set<String> targetTables = new HashSet<>();
        for (String statement : statements) {
            String table = tableForAllowedStatement(statement);
            require(table != null, "only CREATE TABLE, CREATE INDEX and ALTER TABLE ADD are allowed");
            require(table.toLowerCase(Locale.ROOT).startsWith(namespace), "migration target is outside plugin namespace");
            targetTables.add(table.toLowerCase(Locale.ROOT));
            Matcher reference = TABLE_REFERENCE.matcher(statement);
            while (reference.find()) {
                require(reference.group(1).toLowerCase(Locale.ROOT).startsWith(namespace),
                        "migration references a table outside plugin namespace");
            }
        }
        return new SqlValidation(List.copyOf(statements), Set.copyOf(targetTables));
    }

    private String stripComments(String sql) {
        require(sql != null, "migration SQL is required");
        require(!sql.contains("/*!"), "MySQL executable comments are forbidden");
        return sql.replaceAll("(?m)--(?:\\s|$).*$", "")
                .replaceAll("(?m)#.*$", "")
                .replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private List<String> splitStatements(String source) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (quote != 0) {
                current.append(value);
                if (escaped) {
                    escaped = false;
                } else if (value == '\\' && quote != '`') {
                    escaped = true;
                } else if (value == quote) {
                    if (index + 1 < source.length() && source.charAt(index + 1) == quote) {
                        current.append(source.charAt(++index));
                    } else {
                        quote = 0;
                    }
                }
            } else if (value == '\'' || value == '"' || value == '`') {
                quote = value;
                current.append(value);
            } else if (value == ';') {
                addStatement(statements, current);
            } else {
                current.append(value);
            }
        }
        require(quote == 0, "migration SQL contains an unterminated quoted value");
        addStatement(statements, current);
        require(!statements.isEmpty(), "migration SQL contains no statements");
        return List.copyOf(statements);
    }

    private void addStatement(List<String> statements, StringBuilder value) {
        String statement = value.toString().trim();
        value.setLength(0);
        if (!statement.isEmpty()) statements.add(statement);
    }

    private String tableForAllowedStatement(String statement) {
        for (Pattern pattern : List.of(CREATE_TABLE, CREATE_INDEX, ALTER_TABLE_ADD)) {
            Matcher matcher = pattern.matcher(statement);
            if (matcher.matches()) return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private String digest(PluginMigrationRequest request, List<PluginMigrationRequest.Script> scripts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, request.pluginCode(), request.pluginVersion(), request.packageDigest(), request.schemaVersion(),
                    request.phase(), request.rollbackMode(), request.compatibleReaders(), request.tableNamespace());
            for (PluginMigrationRequest.Script script : scripts) updateDigest(digest, script.stepName(), script.sql());
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String... values) {
        for (String value : values) {
            digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
    }

    static String namespace(String pluginCode) {
        require(hasText(pluginCode), "plugin code is required");
        String normalized = pluginCode.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        require(!normalized.isBlank(), "plugin code cannot produce a namespace");
        return "plugin_" + normalized + "_";
    }

    private boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(normalized(first).getBytes(StandardCharsets.US_ASCII),
                normalized(second).getBytes(StandardCharsets.US_ASCII));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record SqlValidation(List<String> statements, Set<String> targetTables) { }
}
