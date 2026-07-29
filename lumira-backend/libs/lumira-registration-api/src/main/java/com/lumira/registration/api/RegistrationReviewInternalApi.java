package com.lumira.registration.api;

import java.util.List;

/**
 * Registration-owned contract consumed by Review.
 *
 * <p>The current modular monolith provides an in-process adapter. A future
 * registration service can expose the same contract through an authenticated
 * internal transport without changing Review application logic.</p>
 */
public interface RegistrationReviewInternalApi {

    boolean stageBelongsToCompetition(Long competitionId, Long stageId);

    List<RegistrationCandidateSnapshotDTO> loadEligibleCandidateSnapshots(
            Long competitionId,
            List<Long> registrationIds
    );
}
