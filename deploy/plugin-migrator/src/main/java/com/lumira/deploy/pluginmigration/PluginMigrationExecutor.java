package com.lumira.deploy.pluginmigration;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class PluginMigrationExecutor {
    private static final String VALIDATION_RECOVERY =
            "Correct the package and create a new higher-epoch expand request; never edit or retry this request";
    private static final String PARTIAL_DDL_RECOVERY =
            "Inspect schema and migration audit, reconcile attempted statements, then create a new higher-epoch expand request";

    private final PluginMigrationRepository repository;
    private final PluginMigrationSafetyValidator validator;

    PluginMigrationExecutor(PluginMigrationRepository repository, PluginMigrationSafetyValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    void approve(Connection connection, Approval approval) throws Exception {
        PluginMigrationRequest request = repository.find(connection, approval.requestId());
        if (request == null) throw new IllegalArgumentException("plugin migration request does not exist");
        requireFence(request, approval);
        validator.validate(request, approval.releaseId());
        if (!repository.approve(connection, request, approval.approver(), approval.reason())) {
            throw new IllegalStateException("plugin migration request was not pending approval or its fence changed");
        }
    }

    ExecutionSummary executeApproved(Connection connection, String releaseId, String executorId, int limit) throws Exception {
        List<PluginMigrationRequest> requests = repository.listApproved(connection, releaseId, limit);
        int claimed = 0;
        int succeeded = 0;
        for (PluginMigrationRequest approved : requests) {
            if (!repository.claim(connection, approved, executorId)) continue;
            claimed++;
            PluginMigrationRequest running = new PluginMigrationRequest(
                    approved.id(), approved.pluginCode(), approved.pluginVersion(), approved.schemaVersion(), approved.expectedSchemaDigest(), approved.phase(),
                    approved.rollbackMode(), approved.compatibleReaders(), approved.tableNamespace(), approved.operationEpoch(),
                    approved.packageDigest(), approved.migrationDigest(), approved.releaseId(), "RUNNING",
                    approved.lifecycleStatus(), approved.scriptPayload());
            boolean ddlAttempted = false;
            long executionLogId = 0L;
            try {
                String fenceToken = UUID.randomUUID().toString();
                executionLogId = repository.startExecutionLog(connection, running, executorId, fenceToken);
                PluginMigrationRequest.Validated migration = validator.validate(running, releaseId);
                for (String sql : migration.statements()) {
                    ddlAttempted = true;
                    try (Statement statement = connection.createStatement()) {
                        statement.setQueryTimeout(120);
                        statement.execute(sql);
                    }
                }
                String actualSchemaDigest = repository.captureSchemaSnapshot(connection, running, migration.targetTables());
                if (hasText(running.expectedSchemaDigest())
                        && !MessageDigest.isEqual(normalizeDigest(running.expectedSchemaDigest())
                        .getBytes(StandardCharsets.US_ASCII), actualSchemaDigest.getBytes(StandardCharsets.US_ASCII))) {
                    throw new IllegalStateException("schema digest mismatch expected="
                            + normalizeDigest(running.expectedSchemaDigest()) + " actual=" + actualSchemaDigest);
                }
                if (!repository.complete(connection, running, executorId, executionLogId, actualSchemaDigest)) {
                    throw new IllegalStateException("migration completion fence changed");
                }
                succeeded++;
            } catch (Exception exception) {
                String failure = safeFailure(exception);
                boolean persisted = repository.fail(connection, running, executorId, executionLogId, failure,
                        ddlAttempted ? PARTIAL_DDL_RECOVERY : VALIDATION_RECOVERY, ddlAttempted);
                if (!persisted) {
                    throw new IllegalStateException("migration failed and its failure fence could not be persisted", exception);
                }
                throw new IllegalStateException("plugin migration request " + running.id() + " failed: " + failure, exception);
            }
        }
        return new ExecutionSummary(requests.size(), claimed, succeeded);
    }

    private void requireFence(PluginMigrationRequest request, Approval approval) {
        if (request.id() != approval.requestId()
                || request.operationEpoch() != approval.operationEpoch()
                || !request.packageDigest().equalsIgnoreCase(approval.packageDigest())
                || !request.migrationDigest().equalsIgnoreCase(approval.migrationDigest())
                || !request.releaseId().equals(approval.releaseId())
                || !"PENDING_APPROVAL".equals(request.requestStatus())) {
            throw new IllegalArgumentException("approval fence does not match the pending request");
        }
    }

    private String safeFailure(Exception exception) {
        Throwable cursor = exception;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        String value = cursor.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        value = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeDigest(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("sha256:") ? normalized.substring("sha256:".length()) : normalized;
    }

    record Approval(long requestId, long operationEpoch, String packageDigest, String migrationDigest,
                    String releaseId, String approver, String reason) {
    }

    record ExecutionSummary(int approved, int claimed, int succeeded) {
    }
}
