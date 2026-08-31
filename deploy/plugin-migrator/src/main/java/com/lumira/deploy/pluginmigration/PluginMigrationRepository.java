package com.lumira.deploy.pluginmigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class PluginMigrationRepository {
    private static final String REQUEST_COLUMNS = """
            id, plugin_code, plugin_version, schema_version, phase, rollback_mode, compatible_readers,
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

    boolean complete(Connection connection, PluginMigrationRequest request, String executorId) throws SQLException {
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
            audit(connection, request, "SUCCEEDED", executorId, "All approved expand statements completed");
            return true;
        });
    }

    boolean fail(Connection connection, PluginMigrationRequest request, String executorId, String failureReason,
                 String recoveryAction, boolean rollbackBlocked) throws SQLException {
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
                resultSet.getString("schema_version"), resultSet.getString("phase"), resultSet.getString("rollback_mode"),
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
