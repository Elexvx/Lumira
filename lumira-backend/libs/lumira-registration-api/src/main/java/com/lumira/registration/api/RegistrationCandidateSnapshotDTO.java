package com.lumira.registration.api;

/**
 * Immutable registration data transferred to Review when a candidate batch is
 * frozen. Review must not query mutable registration tables after this handoff.
 */
public record RegistrationCandidateSnapshotDTO(
        Long registrationId,
        String registrationNo,
        Long competitionId,
        Long teamId,
        Long projectId,
        Long ownerUserId,
        String ownerUserUuid,
        String status,
        String registrationSnapshotJson,
        String teamSnapshotJson,
        String projectSnapshotJson,
        String memberSnapshotJson,
        String collectionSchemaSnapshotJson,
        String materialSnapshotJson
) {
}
