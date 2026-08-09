package com.lumira.saas.modules.review.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.infrastructure.persistence.RowMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcReviewRepositoryTest {

    @Test
    void candidateFreezeReadsOnlyRegistrationsLinkedToTheCompetitionDataset() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.loadCandidateSnapshots(10L, List.of(100L));

        assertThat(database.sql)
                .contains("join competition_registration_dataset_row")
                .contains("join competition_registration_dataset")
                .contains("dataset.competition_id = registration.competition_id")
                .contains("dataset.status = 'ENABLED'")
                .contains("registration.status in ('PAID', 'CONFIRMED')");
        assertThat(database.args).containsExactly(10L, 100L);
    }

    @Test
    void expertTaskScopeRequiresBothStableUserIdentitiesAndUsesBlindSafeSnapshot() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.listOwnedAssignments(7L, "expert-user-uuid");

        assertThat(database.sql)
                .contains("candidate.review_snapshot_json")
                .doesNotContain("candidate.snapshot_json as candidateSnapshotJson")
                .contains("assignment.expert_user_id = ?")
                .contains("assignment.expert_user_uuid = ?");
        assertThat(database.args).containsExactly(7L, "expert-user-uuid");
    }

    @Test
    void assignmentLookupCannotBeClaimedWithARecycledNumericUserId() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.findOwnedAssignment(90L, 7L, "expert-user-uuid");

        assertThat(database.sql)
                .contains("assignment.id = ?")
                .contains("assignment.expert_user_id = ?")
                .contains("assignment.expert_user_uuid = ?");
        assertThat(database.args).containsExactly(90L, 7L, "expert-user-uuid");
    }

    @Test
    void planListAppliesCompetitionAndStageFiltersServerSide() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.listPlans(10L, 20L);

        assertThat(database.sql)
                .contains("competition_id = ?")
                .contains("stage_id = ?")
                .contains("deleted = 0")
                .contains("order by updated_at desc");
        assertThat(database.args).containsExactly(10L, 20L);
    }

    @Test
    void adminCandidateQueryReturnsBothFullAndBlindSafeFrozenSnapshots() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.listCandidates(60L);

        assertThat(database.sql)
                .contains("snapshot_json as snapshotJson")
                .contains("review_snapshot_json as reviewSnapshotJson")
                .contains("batch_id = ?")
                .contains("deleted = 0");
        assertThat(database.args).containsExactly(60L);
    }

    @Test
    void publishedResultQueryScopesTeamAccessByBothStableUserIdentities() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.listOwnedPublishedResults(7L, "student-user-uuid");

        assertThat(database.sql)
                .contains("registration.owner_user_id = ?")
                .contains("registration.owner_user_uuid = ?")
                .contains("find_in_set(cast(registration.team_id as char), ?)")
                .doesNotContain("team_member")
                .contains("publication.status = 'PUBLISHED'")
                .contains("review_aggregate.status = 'FINALIZED'");
        assertThat(database.args).containsExactly(
                7L,
                "student-user-uuid",
                ""
        );
    }

    @Test
    void appealTargetLookupCannotCrossRegistrationOwnershipBoundary() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.findOwnedAppealTarget(500L, 100L, 7L, "student-user-uuid");

        assertThat(database.sql)
                .contains("publication.id = ?")
                .contains("registration.id = ?")
                .contains("registration.owner_user_id = ? and registration.owner_user_uuid = ?")
                .contains("find_in_set(cast(registration.team_id as char), ?)")
                .doesNotContain("team_member");
        assertThat(database.args).containsExactly(
                500L,
                100L,
                7L,
                "student-user-uuid",
                ""
        );
    }

    @Test
    void assignmentExpiryOnlyTouchesOverdueActiveTasksInOpenBatches() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.expireDueAssignments(java.time.LocalDateTime.now());

        assertThat(database.sql)
                .contains("batch.status in ('ASSIGNING', 'IN_REVIEW')")
                .contains("assignment.status in ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS')")
                .contains("assignment.due_at <= ?")
                .contains("assignment.status = 'EXPIRED'");
    }

    @Test
    void correctionRevokesOnlyLatestPublishedVersionForBatch() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcReviewRepository repository = new JdbcReviewRepository(database);

        repository.revokeLatestPublication(
                60L,
                "correction",
                7L,
                "admin-user-uuid",
                java.time.LocalDateTime.now()
        );

        assertThat(database.sql)
                .contains("status = 'REVOKED'")
                .contains("where batch_id = ? and status = 'PUBLISHED'")
                .contains("order by publication_version desc")
                .contains("limit 1");
    }

    private static final class RecordingQueryOperations extends CompetitionSqlOperations {
        private String sql;
        private Object[] args = new Object[0];

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.args = args;
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.args = args;
            return 1;
        }
    }
}
