package com.lumira.api.file;

public record CompetitionStorageSpaceRequest(
        Long competitionId,
        String competitionUuid,
        String competitionTitle,
        Long operatorUserId,
        String operatorUserUuid
) {
}
