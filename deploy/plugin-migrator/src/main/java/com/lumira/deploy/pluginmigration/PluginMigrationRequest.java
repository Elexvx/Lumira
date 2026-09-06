package com.lumira.deploy.pluginmigration;

import java.util.List;

record PluginMigrationRequest(
        long id,
        String pluginCode,
        String pluginVersion,
        String schemaVersion,
        String expectedSchemaDigest,
        String phase,
        String rollbackMode,
        String compatibleReaders,
        String tableNamespace,
        long operationEpoch,
        String packageDigest,
        String migrationDigest,
        String releaseId,
        String requestStatus,
        String lifecycleStatus,
        String scriptPayload
) {
    PluginMigrationRequest(long id, String pluginCode, String pluginVersion, String schemaVersion,
                           String phase, String rollbackMode, String compatibleReaders, String tableNamespace,
                           long operationEpoch, String packageDigest, String migrationDigest, String releaseId,
                           String requestStatus, String lifecycleStatus, String scriptPayload) {
        this(id, pluginCode, pluginVersion, schemaVersion, null, phase, rollbackMode, compatibleReaders,
                tableNamespace, operationEpoch, packageDigest, migrationDigest, releaseId, requestStatus,
                lifecycleStatus, scriptPayload);
    }

    record Script(String stepName, String scriptPath, String sql) {
    }

    record Validated(List<Script> scripts, List<String> statements, List<String> targetTables) {
        Validated(List<Script> scripts, List<String> statements) {
            this(scripts, statements, List.of());
        }
    }
}
