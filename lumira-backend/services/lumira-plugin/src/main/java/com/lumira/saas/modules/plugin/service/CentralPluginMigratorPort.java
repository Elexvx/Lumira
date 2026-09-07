package com.lumira.saas.modules.plugin.service;

import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMigrationRequestEntity;

import java.util.List;

/**
 * Architectural contract for the separately built, separately privileged central Migrator.
 * There is deliberately no Spring/application-DataSource implementation of this interface;
 * the one-shot deployment adapter owns approval, claim, execution and result persistence.
 */
public interface CentralPluginMigratorPort {

    boolean approve(long requestId, long operationEpoch, String packageDigest, String migrationDigest, String releaseId,
                    String approver, String approvalReason);

    List<PluginMigrationRequestEntity> readApproved(int limit);

    boolean claim(long requestId, long operationEpoch, String packageDigest, String migrationDigest, String releaseId);

    boolean complete(long requestId, long operationEpoch, String packageDigest, String migrationDigest, String releaseId);

    boolean fail(long requestId, long operationEpoch, String packageDigest, String migrationDigest, String releaseId,
                 String failureReason, String recoveryAction, boolean rollbackBlocked);
}
