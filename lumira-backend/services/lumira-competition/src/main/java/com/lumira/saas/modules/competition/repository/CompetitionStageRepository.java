package com.lumira.saas.modules.competition.repository;

import java.time.LocalDateTime;

/**
 * Persistence boundary for competition stages, material windows, and generated
 * participant stage forms.
 */
public interface CompetitionStageRepository {

    int updateStageWindow(StageWindowUpdate command);

    StageFormSynchronizationResult synchronizeStageForm(StageFormSynchronization command);

    record Actor(Long userId, String userUuid) {
    }

    record StageWindowUpdate(
            Long competitionId,
            String stageCode,
            String stageName,
            LocalDateTime materialStart,
            LocalDateTime materialEnd,
            LocalDateTime reviewStart,
            LocalDateTime reviewEnd,
            Actor actor,
            LocalDateTime updatedAt
    ) {
    }

    record StageFormSynchronization(
            Long competitionId,
            String stageCode,
            String stageName,
            int sort,
            String formSchemaJson,
            boolean enabled,
            Actor actor,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * Individual write counts preserve optimistic-write handling in the
     * application service without leaking SQL or generated-key operations.
     */
    record StageFormSynchronizationResult(
            int stageWriteCount,
            int formWriteCount,
            boolean createdStage,
            boolean createdForm
    ) {
    }
}
