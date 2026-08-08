package com.lumira.saas.modules.competition.repository;

import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence boundary for competition-management reads.
 *
 * <p>The management application service owns authorization and input normalization;
 * this port owns only persisted competition lookup semantics.</p>
 */
public interface CompetitionManagementRepository {

    CompetitionPage findCompetitions(CompetitionSearch search);

    CompetitionVO.Competition findCompetition(Long id);

    CompetitionVO.Competition findCompetitionByUuid(String competitionUuid);

    CompetitionVO.Competition findPublishedCompetitionByUuid(String competitionUuid);

    CompetitionCreateResult createCompetition(CompetitionCreate command);

    int updateCompetition(CompetitionUpdate command);

    long countActiveRegistrations(Long competitionId);

    int softDeleteCompetition(CompetitionDelete command);

    boolean existsActiveCompetitionNo(String competitionNo);

    record CompetitionSearch(
            String keyword,
            String category,
            String status,
            String locale,
            Boolean featured,
            long offset,
            long limit
    ) {
    }

    record CompetitionPage(List<CompetitionVO.Competition> records, long total) {
    }

    record Actor(Long userId, String userUuid) {
    }

    /**
     * Persisted competition fields have already been normalized by the
     * application service. The adapter owns the SQL shape and generated-key
     * retrieval, while the app service owns validation and authorization.
     */
    record CompetitionCreate(
            String uuid,
            String competitionNo,
            CompetitionDTO.CompetitionUpsertRequest competition,
            Actor actor
    ) {
    }

    record CompetitionCreateResult(Long competitionId, int writeCount) {
    }

    record CompetitionUpdate(
            Long id,
            String competitionUuid,
            String competitionNo,
            String expectedStatus,
            String persistedStatus,
            CompetitionDTO.CompetitionUpsertRequest competition,
            Actor actor,
            LocalDateTime updatedAt
    ) {
    }

    record CompetitionDelete(
            Long id,
            String competitionUuid,
            String competitionNo,
            String expectedStatus,
            Actor actor,
            LocalDateTime updatedAt
    ) {
    }
}
