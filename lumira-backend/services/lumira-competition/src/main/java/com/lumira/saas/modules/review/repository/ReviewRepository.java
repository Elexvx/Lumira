package com.lumira.saas.modules.review.repository;

import com.lumira.saas.modules.review.dto.ReviewDTO;
import com.lumira.saas.modules.review.vo.ReviewVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository {

    boolean stageBelongsToCompetition(Long competitionId, Long stageId);

    Optional<ReviewVO.Plan> findPlan(Long planId);

    Optional<ReviewVO.Plan> findPlanByStage(Long competitionId, Long stageId);

    List<ReviewVO.Plan> listPlans(Long competitionId, Long stageId);

    Long insertPlan(
            ReviewDTO.PlanCreateRequest request,
            String blindMode,
            int requiredReviewerCount,
            int minimumSubmittedCount,
            String aggregateMethod,
            BigDecimal scoreScale,
            int trimHighestCount,
            int trimLowestCount,
            Long operatorId,
            String operatorUuid
    );

    Long insertCriteriaVersion(
            Long planId,
            int versionNo,
            String versionName,
            BigDecimal totalWeight,
            Long operatorId,
            String operatorUuid
    );

    Long insertCriterion(
            Long criteriaVersionId,
            ReviewDTO.CriterionRequest criterion,
            int sortOrder,
            Long operatorId,
            String operatorUuid
    );

    Long findDraftCriteriaVersionId(Long planId);

    int publishCriteriaVersion(
            Long criteriaVersionId,
            String contentHash,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    );

    int markPlanReady(
            Long planId,
            Long criteriaVersionId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    List<ReviewVO.Criterion> listCriteria(Long criteriaVersionId);

    Optional<ReviewVO.Batch> findBatch(Long batchId);

    List<ReviewVO.Batch> listBatches(Long planId, Long competitionId);

    List<ReviewVO.Candidate> listCandidates(Long batchId);

    List<ReviewVO.AdminAssignment> listAssignments(Long batchId);

    int replaceRoster(
            Long batchId,
            List<ExpertRosterCandidate> experts,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    List<ReviewVO.RosterExpert> listRoster(Long batchId);

    List<Long> listSelectedRosterExpertIds(Long batchId);

    Long upsertInvitation(
            Long batchId,
            Long rosterId,
            ExpertRosterCandidate expert,
            String tokenHash,
            LocalDateTime tokenExpiresAt,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    int markInvitationSent(Long invitationId, int attempts, LocalDateTime sentAt);

    int markInvitationFailed(Long invitationId, int attempts, String reason, LocalDateTime failedAt);

    Long enqueueInvitationNotification(
            Long batchId,
            Long invitationId,
            String dedupeKey,
            String recipientEmail,
            String subject,
            String content,
            Long operatorId,
            String operatorUuid,
            LocalDateTime createdAt
    );

    int markInvitationNotificationSent(Long outboxId, int attempts, LocalDateTime sentAt);

    int markInvitationNotificationFailed(
            Long outboxId,
            int attempts,
            String reason,
            LocalDateTime failedAt
    );

    Optional<InvitationContext> findInvitationByTokenHash(String tokenHash);

    Optional<InvitationContext> findInvitationByQrTokenHash(String qrTokenHash);

    int issueInvitationQr(
            Long invitationId,
            String qrTokenHash,
            LocalDateTime qrExpiresAt,
            LocalDateTime openedAt
    );

    int markInvitationCheckedIn(
            Long invitationId,
            Long operatorId,
            String operatorUuid,
            LocalDateTime checkedInAt
    );

    int recordCheckinAttempt(
            Long batchId,
            Long invitationId,
            Long expertId,
            String qrTokenHash,
            String status,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime attemptedAt
    );

    int markBatchAssignmentsConfirmed(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime confirmedAt
    );

    List<ExpertWorkload> listApprovedExpertWorkloads();

    Long insertBatch(
            ReviewVO.Plan plan,
            String batchNo,
            ReviewDTO.BatchCreateRequest request,
            String assignmentStrategy,
            Long operatorId,
            String operatorUuid
    );

    List<CandidateSnapshot> loadCandidateSnapshots(Long competitionId, List<Long> registrationIds);

    Long insertCandidate(
            Long batchId,
            Long registrationId,
            String blindCode,
            String snapshotJson,
            String reviewSnapshotJson,
            String snapshotHash,
            Long operatorId,
            String operatorUuid
    );

    int markBatchReady(
            Long batchId,
            String freezeToken,
            int candidateCount,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime frozenAt
    );

    Optional<AssignmentTarget> findAssignmentTarget(Long batchId, Long candidateId, Long expertId);

    boolean assignmentExists(Long candidateId, Long expertId);

    Long insertAssignment(
            Long batchId,
            AssignmentTarget target,
            BigDecimal reviewerWeight,
            LocalDateTime dueAt,
            Long operatorId,
            String operatorUuid
    );

    int markBatchAssigning(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    int countCandidatesBelowMinimumAssignments(Long batchId, int minimumReviewerCount);

    int markBatchInReview(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    List<ReviewVO.AssignmentTask> listOwnedAssignments(Long userId, String userUuid);

    Optional<AssignmentContext> findOwnedAssignment(Long assignmentId, Long userId, String userUuid);

    int acceptAssignment(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            LocalDateTime acceptedAt
    );

    int declineAssignment(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            String reason,
            LocalDateTime declinedAt
    );

    int revokeAssignment(
            Long batchId,
            Long assignmentId,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime revokedAt
    );

    int expireDueAssignments(LocalDateTime expiredAt);

    Integer findLatestSheetVersion(Long assignmentId);

    Long insertSheet(
            AssignmentContext assignment,
            int versionNo,
            String status,
            BigDecimal totalScore,
            String reviewComment,
            LocalDateTime submittedAt,
            Long userId,
            String userUuid
    );

    Long insertScoreItem(
            Long sheetId,
            ReviewDTO.ScoreItemRequest score,
            Long userId,
            String userUuid
    );

    int markAssignmentInProgress(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            LocalDateTime updatedAt
    );

    int markAssignmentSubmitted(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            LocalDateTime submittedAt
    );

    Optional<ReviewVO.ReviewSheet> findSheet(Long sheetId, Long assignmentId);

    List<ReviewVO.ScoreItem> listScoreItems(Long sheetId);

    int countCandidatesBelowMinimumSubmitted(Long batchId, int minimumSubmittedCount);

    int markBatchAggregating(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    List<AggregationSource> loadAggregationSources(Long batchId);

    int upsertAggregate(
            Long batchId,
            Long candidateId,
            BigDecimal aggregateScore,
            BigDecimal minimumScore,
            BigDecimal maximumScore,
            BigDecimal scoreStddev,
            int submittedReviewerCount,
            int validReviewerCount,
            int rankNo,
            String anomalyFlagsJson,
            Long operatorId,
            String operatorUuid,
            LocalDateTime calculatedAt
    );

    List<ReviewVO.Aggregate> listAggregates(Long batchId);

    int updateAggregateDecision(
            Long batchId,
            Long candidateId,
            String decision,
            String decisionReason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    int countPendingAggregateDecisions(Long batchId);

    int finalizeAggregates(
            Long batchId,
            Long operatorId,
            String operatorUuid,
            LocalDateTime finalizedAt
    );

    int markBatchFinalized(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime finalizedAt
    );

    List<PublicationRow> loadPublicationRows(Long batchId);

    Integer findLatestPublicationVersion(Long batchId);

    Long insertPublication(
            Long batchId,
            int publicationVersion,
            String payloadJson,
            String payloadHash,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    );

    int projectLegacyResult(
            ReviewVO.Batch batch,
            PublicationRow result,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    );

    int markBatchPublished(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    );

    Optional<ReviewVO.Publication> findLatestPublication(Long batchId);

    int revokeLatestPublication(
            Long batchId,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime revokedAt
    );

    int reopenFinalizedAggregates(
            Long batchId,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    int markLegacyProjectionCorrectionPending(
            ReviewVO.Batch batch,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    int reopenPublishedBatch(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    );

    default List<ReviewVO.PublishedResult> listOwnedPublishedResults(Long userId, String userUuid) {
        return listOwnedPublishedResults(userId, userUuid, List.of());
    }

    List<ReviewVO.PublishedResult> listOwnedPublishedResults(Long userId, String userUuid, List<Long> activeTeamIds);

    default Optional<AppealTarget> findOwnedAppealTarget(
            Long publicationId,
            Long registrationId,
            Long userId,
            String userUuid
    ) {
        return findOwnedAppealTarget(publicationId, registrationId, userId, userUuid, List.of());
    }

    Optional<AppealTarget> findOwnedAppealTarget(
            Long publicationId,
            Long registrationId,
            Long userId,
            String userUuid,
            List<Long> activeTeamIds
    );

    Optional<ReviewVO.Appeal> findAppealByPublicationAndRegistration(
            Long publicationId,
            Long registrationId
    );

    Optional<ReviewVO.Appeal> findAppeal(Long appealId);

    Long insertAppeal(
            AppealTarget target,
            String appealNo,
            String reason,
            Long operatorId,
            String operatorUuid
    );

    List<ReviewVO.Appeal> listOwnedAppeals(Long userId, String userUuid);

    List<ReviewVO.Appeal> listAppeals(Long batchId, String status);

    int resolveAppeal(
            Long appealId,
            String decision,
            String resolution,
            Long operatorId,
            String operatorUuid,
            LocalDateTime resolvedAt
    );

    record CandidateSnapshot(
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

    record AssignmentTarget(
            Long candidateId,
            Long registrationId,
            String candidateStatus,
            Long candidateOwnerUserId,
            String candidateOwnerUserUuid,
            Long expertId,
            Long expertUserId,
            String expertUserUuid,
            String expertStatus,
            String approvalStatus,
            String accountStatus,
            boolean identityConflict,
            String candidateSnapshotJson
    ) {
        public AssignmentTarget(
                Long candidateId,
                Long registrationId,
                String candidateStatus,
                Long candidateOwnerUserId,
                String candidateOwnerUserUuid,
                Long expertId,
                Long expertUserId,
                String expertUserUuid,
                String expertStatus,
                String approvalStatus,
                String accountStatus,
                boolean identityConflict
        ) {
            this(
                    candidateId, registrationId, candidateStatus, candidateOwnerUserId, candidateOwnerUserUuid,
                    expertId, expertUserId, expertUserUuid, expertStatus, approvalStatus, accountStatus,
                    identityConflict, null
            );
        }
    }

    record ExpertWorkload(Long expertId, int activeAssignmentCount) {
    }

    record ExpertRosterCandidate(
            Long expertId,
            Long userId,
            String userUuid,
            String name,
            String email,
            String status,
            String approvalStatus,
            String accountStatus
    ) {
    }

    record InvitationContext(
            Long invitationId,
            Long batchId,
            String batchName,
            Long rosterId,
            Long expertId,
            Long expertUserId,
            String expertUserUuid,
            String expertName,
            String email,
            String invitationStatus,
            String tokenHash,
            LocalDateTime tokenExpiresAt,
            LocalDateTime qrExpiresAt,
            LocalDateTime checkedInAt,
            LocalDateTime sentAt,
            String failureReason
    ) {
    }

    record AssignmentContext(
            Long assignmentId,
            Long batchId,
            String batchStatus,
            Long candidateId,
            Long expertId,
            String assignmentStatus,
            Integer assignmentVersion,
            Long criteriaVersionId,
            BigDecimal scoreScale,
            LocalDateTime dueAt,
            List<ReviewVO.Criterion> criteria
    ) {
    }

    record WeightedScore(BigDecimal score, BigDecimal weight) {
    }

    record AggregationSource(Long candidateId, List<WeightedScore> submittedScores) {
    }

    record PublicationRow(
            Long candidateId,
            Long registrationId,
            Long ownerUserId,
            String ownerUserUuid,
            String blindCode,
            BigDecimal aggregateScore,
            Integer rankNo,
            String decision
    ) {
    }

    record AppealTarget(
            Long publicationId,
            Integer publicationVersion,
            Long batchId,
            Long competitionId,
            Long stageId,
            Long candidateId,
            Long registrationId,
            BigDecimal aggregateScore,
            Integer rankNo,
            String decision,
            LocalDateTime publishedAt
    ) {
    }
}
