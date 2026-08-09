package com.lumira.saas.modules.competition.repository;

public interface RegistrationDatasetRepository {

    int createDataset(
            Long competitionId,
            String competitionTitle,
            Long operatorId,
            String operatorUuid
    );

    int linkRegistration(
            Long competitionId,
            Long registrationId,
            Long ownerUserId,
            String ownerUserUuid
    );

    int unlinkRegistration(
            Long registrationId,
            Long operatorId,
            String operatorUuid
    );

    boolean isLinked(Long competitionId, Long registrationId);
}
