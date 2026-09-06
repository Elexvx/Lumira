package com.lumira.deploy.pluginmigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class PluginMigrationRepository {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final String REQUEST_COLUMNS = """
            id, plugin_code, plugin_version, schema_version, expected_schema_digest, phase, rollback_mode, compatible_readers,
            table_namespace, operation_epoch, package_digest, migration_digest, release_id,
            request_status, lifecycle_status, script_payload
            """;

    List<PluginMigrationRequest> listApproved(Connection connection, String releaseId, int limit) throws SQLException {
        String sql = "select " + REQUEST_COLUMNS + " from sys_plugin_migration_request "
                + "where request_status = 'APPROVED' and phase = 'EXPAND' and release_id = ? order by id asc limit ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, releaseId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PluginMigrationRequest> requests = new ArrayList<>();
                while (resultSet.next()) requests.add(read(resultSet));
                return List.copyOf(requests);
            }
        }
    }

    PluginMigrationRequest find(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select " + REQUEST_COLUMNS + " from sys_plugin_migration_request where id = ?")) {
            statement.setLong(1, requestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? read(resultSet) : null;
            }
        }
    }

    boolean approve(Connection connection, PluginMigrationRequest request, String approver, String reason) throws SQLException {
        return transactional(connection, () -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    update sys_plugin_migration_request
                    set request_status = 'APPROVED', approved_by = ?, approval_reason = ?,
                        approved_at = current_timestamp, updated_at = current_timestamp
                    where id = ? and operation_epoch = ? and package_digest = ? and migration_digest = ?
                      and release_id = ? and request_status = 'PENDING_APPROVAL' and lifecycle_status = 'MIGRATION_PENDING'
                      and phase = 'EXPAND'
                    """)) {
                statement.setString(1, approver);
                statement.setString(2, reason);
                bindFence(statement, 3, request);
                updated = statement.executeUpdate();
            }
            if (updated == 1) audit(connection, request, "APPROVED", approver, reason);
            return updated == 1;
        });
    }

    boolean claim(Connection connection, PluginMigrationRequest request, String executorId) throws SQLException {
        return transactional(connection, () -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    update sys_plugin_migration_request
                    set request_status = 'RUNNING', started_at = current_timestamp, updated_at = current_timestamp
                    where id = ? and operation_epoch = ? and package_digest = ? and migration_digest = ?
                      and release_id = ? and request_status = 'APPROVED' and lifecycle_status = 'MIGRATION_PENDING'
                      and phase = 'EXPAND' and approved_by is not null and approval_reason is not null
                    """)) {
                bindFence(statement, 1, request);
                updated = statement.executeUpdate();
            }
            if (updated == 1) audit(connection, request, "CLAIMED", executorId, "Approved request claimed by one-shot migrator");
            return updated == 1;
        });
    }

    long startExecutionLog(Connection connection, PluginMigrationRequest request, String executorId,
                           String fenceToken) throws SQLException {
        return transactional(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into plugin_migration_execution_log (
                        migration_request_id, plugin_code, release_id, migration_digest, schema_version,
                        executor_type, executor_id, fence_token, status
                    ) values (?, ?, ?, ?, ?, 'CENTRAL_MIGRATOR', ?, ?, 'STARTED')
                    """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, request.id());
                statement.setString(2, request.pluginCode());
                statement.setString(3, request.releaseId());
                statement.setString(4, request.migrationDigest());
                statement.setString(5, request.schemaVersion());
                statement.setString(6, bounded(executorId, 128));
                statement.setString(7, bounded(fenceToken, 128));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("plugin migration execution log id was not generated");
                    return keys.getLong(1);
                }
            }
        });
    }

    String captureSchemaSnapshot(Connection connection, PluginMigrationRequest request,
                                 List<String> targetTables) throws SQLException {
        return transactional(connection, () -> {
            List<SnapshotRow> snapshots = new ArrayList<>();
            for (String table : targetTables.stream().distinct().sorted().toList()) {
                requireIdentifier(table);
                String quotedTable = "`" + table + "`";
                try (PreparedStatement statement = connection.prepareStatement("show create table " + quotedTable);
                     ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) throw new SQLException("schema snapshot table does not exist: " + table);
                    snapshots.add(new SnapshotRow("TABLE", table, hash(resultSet.getString(2))));
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        select column_name, column_type, is_nullable, column_default, extra, column_key,
                               generation_expression
                        from information_schema.columns
                        where table_schema = database() and table_name = ?
                        order by ordinal_position
                        """)) {
                    statement.setString(1, table);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String objectName = table + "." + resultSet.getString("column_name");
                            String definition = join(
                                    resultSet.getString("column_name"), resultSet.getString("column_type"),
                                    resultSet.getString("is_nullable"), resultSet.getString("column_default"),
                                    resultSet.getString("extra"), resultSet.getString("column_key"),
                                    resultSet.getString("generation_expression"));
                            snapshots.add(new SnapshotRow("COLUMN", objectName, hash(definition)));
                        }
                    }
                }
                Map<String, StringBuilder> indexes = new LinkedHashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        select index_name, non_unique, seq_in_index, column_name, index_type
                        from information_schema.statistics
                        where table_schema = database() and table_name = ?
                        order by index_name, seq_in_index
                        """)) {
                    statement.setString(1, table);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String indexName = resultSet.getString("index_name");
                            StringBuilder definition = indexes.computeIfAbsent(indexName, ignored -> new StringBuilder());
                            definition.append(join(resultSet.getString("non_unique"),
                                    resultSet.getString("seq_in_index"), resultSet.getString("column_name"),
                                    resultSet.getString("index_type"))).append('\u0000');
                        }
                    }
                }
                indexes.forEach((indexName, definition) -> snapshots.add(
                        new SnapshotRow("INDEX", table + "#" + indexName, hash(definition.toString()))));
            }
            snapshots.sort(Comparator.comparing(SnapshotRow::objectType).thenComparing(SnapshotRow::objectName));
            try (PreparedStatement delete = connection.prepareStatement(
                    "delete from plugin_schema_snapshot where migration_request_id = ?")) {
                delete.setLong(1, request.id());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into plugin_schema_snapshot (
                        migration_request_id, plugin_code, schema_version, object_type,
                        object_name, definition_hash, release_id
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (SnapshotRow snapshot : snapshots) {
                    insert.setLong(1, request.id());
                    insert.setString(2, request.pluginCode());
                    insert.setString(3, request.schemaVersion());
                    insert.setString(4, snapshot.objectType());
                    insert.setString(5, snapshot.objectName());
                    insert.setString(6, snapshot.definitionHash());
                    insert.setString(7, request.releaseId());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            MessageDigest aggregate = sha256();
            for (SnapshotRow snapshot : snapshots) update(aggregate, snapshot.objectType(), snapshot.objectName(), snapshot.definitionHash());
            return HexFormat.of().formatHex(aggregate.digest());
        });
    }

    boolean complete(Connection connection, PluginMigrationRequest request, String executorId,
                     long executionLogId, String actualSchemaDigest) throws SQLException {
        return transactional(connection, () -> {
            int requestUpdated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    update sys_plugin_migration_request
                    set request_status = 'SUCCEEDED', lifecycle_status = 'MIGRATED',
                        failure_reason = null, recovery_action = null,
                        finished_at = current_timestamp, updated_at = current_timestamp
                    where id = ? and operation_epoch = ? and package_digest = ? and migration_digest = ?
                      and release_id = ? and request_status = 'RUNNING' and lifecycle_status = 'MIGRATION_PENDING'
                    """)) {
                bindFence(statement, 1, request);
                requestUpdated = statement.executeUpdate();
            }
            if (requestUpdated != 1) return false;
            int versionUpdated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    update sys_plugin_version
                    set lifecycle_status = 'MIGRATED', schema_status = 'READY', updated_at = current_timestamp
                    where plugin_code = ? and version = ? and deleted = 0 and lifecycle_status = 'MIGRATION_PENDING'
                    """)) {
                statement.setString(1, request.pluginCode());
                statement.setString(2, request.pluginVersion());
                versionUpdated = statement.executeUpdate();
            }
            if (versionUpdated != 1) throw new SQLException("plugin lifecycle changed before migration completion");
            try (PreparedStatement statement = connection.prepareStatement("""
                    update plugin_migration_execution_log
                    set status = 'SUCCESS', actual_schema_digest = ?, finished_at = current_timestamp,
                        updated_at = current_timestamp
                    where id = ? and migration_request_id = ? and status = 'STARTED'
                    """)) {
                statement.setString(1, actualSchemaDigest);
                statement.setLong(2, executionLogId);
                statement.setLong(3, request.id());
                if (statement.executeUpdate() != 1) throw new SQLException("plugin migration execution log was not active");
            }
            audit(connection, request, "SUCCEEDED", executorId, "All approved expand statements completed");
            return true;
        });
    }

    boolean fail(Connection connection, PluginMigrationRequest request, String executorId, long executionLogId,
                 String failureReason, String recoveryAction, boolean rollbackBlocked) throws SQLException {
        return transactional(connection, () -> {
            String lifecycle = rollbackBlocked ? "ROLLBACK_BLOCKED" : "FAILED";
            int requestUpdated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    update sys_plugin_migration_request
                    set request_status = 'FAILED', lifecycle_status = ?, failure_reason = ?, recovery_action = ?,
                        finished_at = current_timestamp, updated_at = current_timestamp
                    where id = ? and operation_epoch = ? and package_digest = ? and migration_digest = ?
                      and release_id = ? and request_status = 'RUNNING'
                    """)) {
                statement.setString(1, lifecycle);
                statement.setString(2, bounded(failureReason, 1024));
                statement.setString(3, bounded(recoveryAction, 1024));
                bindFence(statement, 4, request);
                requestUpdated = statement.executeUpdate();
            }
            if (requestUpdated != 1) return false;
            try (PreparedStatement statement = connection.prepareStatement("""
                    update sys_plugin_version
                    set lifecycle_status = ?, schema_status = 'FAILED', updated_at = current_timestamp
                    where plugin_code = ? and version = ? and deleted = 0 and lifecycle_status = 'MIGRATION_PENDING'
                    """)) {
                statement.setString(1, lifecycle);
                statement.setString(2, request.pluginCode());
                statement.setString(3, request.pluginVersion());
                statement.executeUpdate();
            }
            if (executionLogId > 0L) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        update plugin_migration_execution_log
                        set status = 'FAILED', error_code = ?, error_message = ?, finished_at = current_timestamp,
                            updated_at = current_timestamp
                        where id = ? and migration_request_id = ? and status = 'STARTED'
                        """)) {
                    statement.setString(1, rollbackBlocked ? "PARTIAL_DDL" : "VALIDATION_FAILED");
                    statement.setString(2, bounded(failureReason, 1024));
                    statement.setLong(3, executionLogId);
                    statement.setLong(4, request.id());
                    if (statement.executeUpdate() != 1) throw new SQLException("plugin migration execution log was not active");
                }
            }
            audit(connection, request, lifecycle, executorId, bounded(failureReason, 1024));
            return true;
        });
    }

    private void audit(Connection connection, PluginMigrationRequest request, String eventType,
                       String actor, String detail) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into sys_plugin_migration_audit (
                    request_id, plugin_code, plugin_version, event_type, operation_epoch,
                    package_digest, migration_digest, release_id, actor, detail_message
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, request.id());
            statement.setString(2, request.pluginCode());
            statement.setString(3, request.pluginVersion());
            statement.setString(4, eventType);
            statement.setLong(5, request.operationEpoch());
            statement.setString(6, request.packageDigest());
            statement.setString(7, request.migrationDigest());
            statement.setString(8, request.releaseId());
            statement.setString(9, bounded(actor, 128));
            statement.setString(10, bounded(detail, 1024));
            statement.executeUpdate();
        }
    }

    private void bindFence(PreparedStatement statement, int start, PluginMigrationRequest request) throws SQLException {
        statement.setLong(start, request.id());
        statement.setLong(start + 1, request.operationEpoch());
        statement.setString(start + 2, request.packageDigest());
        statement.setString(start + 3, request.migrationDigest());
        statement.setString(start + 4, request.releaseId());
    }

    private PluginMigrationRequest read(ResultSet resultSet) throws SQLException {
        return new PluginMigrationRequest(
                resultSet.getLong("id"), resultSet.getString("plugin_code"), resultSet.getString("plugin_version"),
                resultSet.getString("schema_version"), resultSet.getString("expected_schema_digest"),
                resultSet.getString("phase"), resultSet.getString("rollback_mode"),
                resultSet.getString("compatible_readers"), resultSet.getString("table_namespace"),
                resultSet.getLong("operation_epoch"), resultSet.getString("package_digest"),
                resultSet.getString("migration_digest"), resultSet.getString("release_id"),
                resultSet.getString("request_status"), resultSet.getString("lifecycle_status"),
                resultSet.getString("script_payload")
        );
    }

    private String bounded(String value, int max) {
        String normalized = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private void requireIdentifier(String value) throws SQLException {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new SQLException("schema snapshot object name is invalid");
        }
    }

    private String join(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) result.append(value == null ? "" : value).append('\u0000');
        return result.toString();
    }

    private String hash(String value) {
        return HexFormat.of().formatHex(sha256().digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void update(MessageDigest digest, String... values) {
        for (String value : values) {
            digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
    }

    private record SnapshotRow(String objectType, String objectName, String definitionHash) { }

    private <T> T transactional(Connection connection, SqlWork<T> work) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T value = work.execute();
            connection.commit();
            return value;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute() throws SQLException;
    }
}
