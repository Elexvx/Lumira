package com.lumira.deploy.pluginmigration;

import java.util.List;

record PluginMigrationRequest(
        long id,
        String pluginCode,
        String pluginVersion,
        String schemaVersion,
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
    record Script(String stepName, String scriptPath, String sql) {
    }

    record Validated(List<Script> scripts, List<String> statements) {
    }
}
