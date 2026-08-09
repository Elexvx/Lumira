package com.lumira.saas.modules.competition.repository;

/**
 * Owner port for durable competition-registration writes.
 */
public interface RegistrationPersistencePort {

    Long createRegistration(CreateRegistrationCommand command);

    record CreateRegistrationCommand(
            String registrationNo,
            Long competitionId,
            Long teamId,
            Long projectId,
            Long ownerUserId,
            String ownerUserUuid,
            String feeMode,
            Long entryFeeMinor,
            Integer memberCount,
            Long payableAmountMinor,
            String currency,
            String registrationSnapshotJson,
            String teamSnapshotJson,
            String projectSnapshotJson,
            String memberSnapshotJson,
            String collectionSchemaSnapshotJson
    ) {
    }
}
