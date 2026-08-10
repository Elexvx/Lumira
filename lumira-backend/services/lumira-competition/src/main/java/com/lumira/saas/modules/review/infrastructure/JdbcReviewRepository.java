package com.lumira.saas.modules.review.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.review.dto.ReviewDTO;
import com.lumira.saas.modules.review.repository.ReviewRepository;
import com.lumira.saas.modules.review.vo.ReviewVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReviewRepository implements ReviewRepository {

    private static final String PLAN_SELECT = """
            select id, competition_id as competitionId, stage_id as stageId, plan_name as planName,
                   status, blind_mode as blindMode, required_reviewer_count as requiredReviewerCount,
                   minimum_submitted_count as minimumSubmittedCount, aggregate_method as aggregateMethod,
                   score_scale as scoreScale, trim_highest_count as trimHighestCount,
                   trim_lowest_count as trimLowestCount, criteria_version_id as criteriaVersionId,
                   version, created_at as createdAt, updated_at as updatedAt
              from competition_review_plan
            """;

    private static final String BATCH_SELECT = """
            select id, plan_id as planId, competition_id as competitionId, stage_id as stageId,
                   criteria_version_id as criteriaVersionId, batch_no as batchNo, batch_name as batchName,
                   batch_type as batchType, status, assignment_strategy as assignmentStrategy,
                   minimum_reviewer_count as minimumReviewerCount,
                   reviewer_count_per_candidate as reviewerCountPerCandidate,
                   expert_min_assignments as expertMinAssignments,
                   expert_target_assignments as expertTargetAssignments,
                   expert_max_assignments as expertMaxAssignments,
                   candidate_count as candidateCount,
                   freeze_token as freezeToken, frozen_at as frozenAt,
                   assignment_confirmed_at as assignmentConfirmedAt,
                   review_deadline as reviewDeadline,
                   finalized_at as finalizedAt, published_at as publishedAt, version,
                   created_at as createdAt, updated_at as updatedAt
              from competition_review_batch
            """;

    private static final String APPEAL_SELECT = """
            select appeal.id, appeal.publication_id as publicationId,
                   publication.batch_id as batchId,
                   batch.competition_id as competitionId, batch.stage_id as stageId,
                   appeal.candidate_id as candidateId, appeal.registration_id as registrationId,
                   appeal.appeal_no as appealNo,
                   review_aggregate.aggregate_score as aggregateScore,
                   review_aggregate.rank_no as rankNo, review_aggregate.decision,
                   appeal.appeal_reason as appealReason, appeal.status,
                   appeal.resolution, appeal.resolved_by as resolvedBy,
                   appeal.resolved_by_uuid as resolvedByUuid, appeal.resolved_at as resolvedAt,
                   appeal.created_by as createdBy, appeal.created_by_uuid as createdByUuid,
                   appeal.created_at as createdAt, appeal.updated_at as updatedAt
              from competition_review_appeal appeal
              join competition_review_publication publication
                on publication.id = appeal.publication_id and publication.deleted = 0
              join competition_review_batch batch
                on batch.id = publication.batch_id and batch.deleted = 0
              join competition_review_aggregate review_aggregate
                on review_aggregate.batch_id = batch.id
               and review_aggregate.candidate_id = appeal.candidate_id
               and review_aggregate.deleted = 0
            """;

    private final CompetitionSqlOperations database;

    public JdbcReviewRepository(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public boolean stageBelongsToCompetition(Long competitionId, Long stageId) {
        return database.exists(
                """
                        select 1
                          from aiadc_competition competition
                          join competition_stage stage
                            on stage.competition_id = competition.id
                           and stage.id = ?
                           and stage.deleted = 0
                         where competition.id = ? and competition.deleted = 0
                         limit 1
                        """,
                stageId,
                competitionId
        );
    }

    @Override
    public Optional<ReviewVO.Plan> findPlan(Long planId) {
        return database.query(
                PLAN_SELECT + " where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ReviewVO.Plan.class),
                planId
        ).stream().findFirst();
    }

    @Override
    public Optional<ReviewVO.Plan> findPlanByStage(Long competitionId, Long stageId) {
        return database.query(
                PLAN_SELECT + " where competition_id = ? and stage_id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ReviewVO.Plan.class),
                competitionId,
                stageId
        ).stream().findFirst();
    }

    @Override
    public List<ReviewVO.Plan> listPlans(Long competitionId, Long stageId) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where deleted = 0");
        if (competitionId != null) {
            where.append(" and competition_id = ?");
            args.add(competitionId);
        }
        if (stageId != null) {
            where.append(" and stage_id = ?");
            args.add(stageId);
        }
        return database.query(
                PLAN_SELECT + where + " order by updated_at desc, id desc",
                new BeanPropertyRowMapper<>(ReviewVO.Plan.class),
                args.toArray()
        );
    }

    @Override
    public Long insertPlan(
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
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_plan (
                            competition_id, stage_id, plan_name, status, blind_mode,
                            required_reviewer_count, minimum_submitted_count, aggregate_method,
                            score_scale, trim_highest_count, trim_lowest_count, version,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 0)
                        """,
                request.getCompetitionId(),
                request.getStageId(),
                request.getPlanName().trim(),
                blindMode,
                requiredReviewerCount,
                minimumSubmittedCount,
                aggregateMethod,
                scoreScale,
                trimHighestCount,
                trimLowestCount,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public Long insertCriteriaVersion(
            Long planId,
            int versionNo,
            String versionName,
            BigDecimal totalWeight,
            Long operatorId,
            String operatorUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_criteria_version (
                            plan_id, version_no, version_name, status, total_weight,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, 0)
                        """,
                planId,
                versionNo,
                versionName,
                totalWeight,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public Long insertCriterion(
            Long criteriaVersionId,
            ReviewDTO.CriterionRequest criterion,
            int sortOrder,
            Long operatorId,
            String operatorUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_criterion (
                            criteria_version_id, criterion_code, criterion_name, description,
                            weight, maximum_score, required, sort_order,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                criteriaVersionId,
                criterion.getCode().trim(),
                criterion.getName().trim(),
                criterion.getDescription(),
                criterion.getWeight(),
                criterion.getMaximumScore(),
                Boolean.FALSE.equals(criterion.getRequired()) ? 0 : 1,
                sortOrder,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public Long findDraftCriteriaVersionId(Long planId) {
        return database.queryForObject(
                """
                        select id
                          from competition_review_criteria_version
                         where plan_id = ? and status = 'DRAFT' and deleted = 0
                         order by version_no desc, id desc
                         limit 1
                        """,
                Long.class,
                planId
        );
    }

    @Override
    public int publishCriteriaVersion(
            Long criteriaVersionId,
            String contentHash,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    ) {
        return database.update(
                """
                        update competition_review_criteria_version
                           set status = 'PUBLISHED', content_hash = ?, published_at = ?,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'DRAFT' and deleted = 0
                        """,
                contentHash,
                publishedAt,
                operatorId,
                operatorUuid,
                publishedAt,
                criteriaVersionId
        );
    }

    @Override
    public int markPlanReady(
            Long planId,
            Long criteriaVersionId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_plan
                           set status = 'READY', criteria_version_id = ?, version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'DRAFT' and version = ? and deleted = 0
                        """,
                criteriaVersionId,
                operatorId,
                operatorUuid,
                updatedAt,
                planId,
                expectedVersion
        );
    }

    @Override
    public List<ReviewVO.Criterion> listCriteria(Long criteriaVersionId) {
        return database.query(
                """
                        select id, criteria_version_id as criteriaVersionId, criterion_code as criterionCode,
                               criterion_name as criterionName, description, weight,
                               maximum_score as maximumScore, required, sort_order as sortOrder
                          from competition_review_criterion
                         where criteria_version_id = ? and deleted = 0
                         order by sort_order asc, id asc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.Criterion.class),
                criteriaVersionId
        );
    }

    @Override
    public Optional<ReviewVO.Batch> findBatch(Long batchId) {
        return database.query(
                BATCH_SELECT + " where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ReviewVO.Batch.class),
                batchId
        ).stream().findFirst();
    }

    @Override
    public List<ReviewVO.Batch> listBatches(Long planId, Long competitionId) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where deleted = 0");
        if (planId != null) {
            where.append(" and plan_id = ?");
            args.add(planId);
        }
        if (competitionId != null) {
            where.append(" and competition_id = ?");
            args.add(competitionId);
        }
        return database.query(
                BATCH_SELECT + where + " order by created_at desc, id desc",
                new BeanPropertyRowMapper<>(ReviewVO.Batch.class),
                args.toArray()
        );
    }

    @Override
    public List<ReviewVO.Candidate> listCandidates(Long batchId) {
        return database.query(
                """
                        select id, batch_id as batchId, registration_id as registrationId,
                               blind_code as blindCode, status, snapshot_json as snapshotJson,
                               review_snapshot_json as reviewSnapshotJson, snapshot_hash as snapshotHash,
                               created_at as createdAt
                          from competition_review_candidate
                         where batch_id = ? and deleted = 0
                         order by id asc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.Candidate.class),
                batchId
        );
    }

    @Override
    public List<ReviewVO.AdminAssignment> listAssignments(Long batchId) {
        return database.query(
                """
                        select id, batch_id as batchId, candidate_id as candidateId,
                               expert_id as expertId, expert_user_id as expertUserId,
                               expert_user_uuid as expertUserUuid, reviewer_weight as reviewerWeight,
                               status, due_at as dueAt,
                               accepted_at as acceptedAt, declined_at as declinedAt,
                               decline_reason as declineReason, expired_at as expiredAt,
                               revoked_at as revokedAt, revoke_reason as revokeReason,
                               submitted_at as submittedAt, invitation.status as invitationStatus,
                               invitation.checked_in_at as checkedInAt, version
                          from competition_review_assignment assignment
                          left join competition_review_invitation invitation
                            on invitation.batch_id = assignment.batch_id
                           and invitation.expert_id = assignment.expert_id
                           and invitation.deleted = 0
                         where assignment.batch_id = ? and assignment.deleted = 0
                         order by assignment.candidate_id asc, assignment.expert_id asc, assignment.id asc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.AdminAssignment.class),
                batchId
        );
    }

    @Override
    public int replaceRoster(
            Long batchId,
            List<ExpertRosterCandidate> experts,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        int invalidatedInvitations = database.update(
                """
                        update competition_review_invitation
                           set status = 'REPLACED', deleted = 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where batch_id = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId
        );
        int changed = database.update(
                """
                        update competition_review_roster
                           set status = 'REMOVED', deleted = 1,
                               removed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where batch_id = ? and deleted = 0
                        """,
                updatedAt,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId
        );
        database.update(
                """
                        update competition_review_batch
                           set assignment_confirmed_at = null,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId
        );
        for (ExpertRosterCandidate expert : experts) {
            database.update(
                    """
                            insert into competition_review_roster (
                                batch_id, expert_id, expert_user_id, expert_user_uuid,
                                expert_name, email, status, selected_at,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, 'SELECTED', ?, ?, ?, ?, ?, 0)
                            on duplicate key update
                                expert_user_id = values(expert_user_id),
                                expert_user_uuid = values(expert_user_uuid),
                                expert_name = values(expert_name), email = values(email),
                                status = 'SELECTED', selected_at = values(selected_at),
                                removed_at = null, deleted = 0,
                                updated_by = values(updated_by), updated_by_uuid = values(updated_by_uuid),
                                updated_at = values(updated_at)
                            """,
                    batchId,
                    expert.expertId(),
                    expert.userId(),
                    expert.userUuid(),
                    expert.name(),
                    expert.email(),
                    updatedAt,
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid
            );
        }
        return invalidatedInvitations + changed + experts.size();
    }

    @Override
    public List<ReviewVO.RosterExpert> listRoster(Long batchId) {
        return database.query(
                """
                        select roster.id, roster.batch_id as batchId, roster.expert_id as expertId,
                               roster.expert_user_id as expertUserId, roster.expert_user_uuid as expertUserUuid,
                               roster.expert_name as expertName, roster.email, roster.status,
                               invitation.status as invitationStatus,
                               invitation.send_attempts as invitationAttempts,
                               invitation.failure_reason as invitationFailureReason,
                               invitation.sent_at as invitationSentAt,
                               invitation.checked_in_at as checkedInAt
                          from competition_review_roster roster
                          left join competition_review_invitation invitation
                            on invitation.batch_id = roster.batch_id
                           and invitation.expert_id = roster.expert_id
                           and invitation.deleted = 0
                         where roster.batch_id = ? and roster.deleted = 0
                         order by roster.id asc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.RosterExpert.class),
                batchId
        );
    }

    @Override
    public List<Long> listSelectedRosterExpertIds(Long batchId) {
        return database.queryForList(
                """
                        select expert_id
                          from competition_review_roster
                         where batch_id = ? and status = 'SELECTED' and deleted = 0
                         order by id asc
                        """,
                Long.class,
                batchId
        );
    }

    @Override
    public Long upsertInvitation(
            Long batchId,
            Long rosterId,
            ExpertRosterCandidate expert,
            String tokenHash,
            LocalDateTime tokenExpiresAt,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        int changed = database.update(
                """
                        insert into competition_review_invitation (
                            batch_id, roster_id, expert_id, expert_user_id, expert_user_uuid,
                            email, token_hash, token_expires_at, status, send_attempts,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, 0)
                        on duplicate key update
                            roster_id = values(roster_id), expert_user_id = values(expert_user_id),
                            expert_user_uuid = values(expert_user_uuid), email = values(email),
                            token_hash = values(token_hash), token_expires_at = values(token_expires_at),
                            status = 'PENDING', sent_at = null, opened_at = null,
                            qr_token_hash = null, qr_expires_at = null, qr_used_at = null,
                            checked_in_at = null, checked_in_by = null, checked_in_by_uuid = null,
                            send_attempts = 0, failure_reason = null,
                            updated_by = values(updated_by), updated_by_uuid = values(updated_by_uuid),
                            updated_at = values(updated_at), deleted = 0
                        """,
                batchId,
                rosterId,
                expert.expertId(),
                expert.userId(),
                expert.userUuid(),
                expert.email(),
                tokenHash,
                tokenExpiresAt,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return changed > 0 ? database.queryForObject(
                """
                        select id from competition_review_invitation
                         where batch_id = ? and expert_id = ? and deleted = 0 limit 1
                        """,
                Long.class,
                batchId,
                expert.expertId()
        ) : null;
    }

    @Override
    public int markInvitationSent(Long invitationId, int attempts, LocalDateTime sentAt) {
        return database.update(
                """
                        update competition_review_invitation
                           set status = 'SENT', sent_at = ?, send_attempts = ?, failure_reason = null,
                               updated_at = ?
                         where id = ? and deleted = 0
                        """,
                sentAt,
                attempts,
                sentAt,
                invitationId
        );
    }

    @Override
    public int markInvitationFailed(Long invitationId, int attempts, String reason, LocalDateTime failedAt) {
        return database.update(
                """
                        update competition_review_invitation
                           set status = 'FAILED', send_attempts = ?, failure_reason = ?, updated_at = ?
                         where id = ? and deleted = 0
                        """,
                attempts,
                reason,
                failedAt,
                invitationId
        );
    }

    @Override
    public Long enqueueInvitationNotification(
            Long batchId,
            Long invitationId,
            String dedupeKey,
            String recipientEmail,
            String subject,
            String content,
            Long operatorId,
            String operatorUuid,
            LocalDateTime createdAt
    ) {
        int changed = database.update(
                """
                        insert into competition_review_notification_outbox (
                            batch_id, invitation_id, dedupe_key, recipient_email, subject, content,
                            status, attempts, next_retry_at,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            recipient_email = values(recipient_email), subject = values(subject),
                            content = values(content),
                            status = case when status = 'DELIVERED' then status else 'PENDING' end,
                            next_retry_at = values(next_retry_at),
                            updated_by = values(updated_by), updated_by_uuid = values(updated_by_uuid),
                            updated_at = values(updated_at), deleted = 0
                        """,
                batchId,
                invitationId,
                dedupeKey,
                recipientEmail,
                subject,
                content,
                createdAt,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return changed > 0 ? database.queryForObject(
                """
                        select id from competition_review_notification_outbox
                         where dedupe_key = ? and deleted = 0 limit 1
                        """,
                Long.class,
                dedupeKey
        ) : null;
    }

    @Override
    public int markInvitationNotificationSent(Long outboxId, int attempts, LocalDateTime sentAt) {
        return database.update(
                """
                        update competition_review_notification_outbox
                           set status = 'DELIVERED', sent_at = ?, attempts = ?, failure_reason = null,
                               next_retry_at = null, updated_at = ?
                         where id = ? and deleted = 0
                        """,
                sentAt,
                attempts,
                sentAt,
                outboxId
        );
    }

    @Override
    public int markInvitationNotificationFailed(
            Long outboxId,
            int attempts,
            String reason,
            LocalDateTime failedAt
    ) {
        return database.update(
                """
                        update competition_review_notification_outbox
                           set status = 'FAILED', attempts = ?, failure_reason = ?,
                               next_retry_at = ?, updated_at = ?
                         where id = ? and deleted = 0
                        """,
                attempts,
                reason,
                failedAt,
                failedAt,
                outboxId
        );
    }

    @Override
    public Optional<InvitationContext> findInvitationByTokenHash(String tokenHash) {
        return findInvitation("invitation.token_hash = ?", tokenHash);
    }

    @Override
    public Optional<InvitationContext> findInvitationByQrTokenHash(String qrTokenHash) {
        return findInvitation("invitation.qr_token_hash = ?", qrTokenHash);
    }

    private Optional<InvitationContext> findInvitation(String predicate, String value) {
        return database.query(
                ("""
                        select invitation.id as invitationId, invitation.batch_id as batchId,
                               batch.batch_name as batchName, invitation.roster_id as rosterId,
                               invitation.expert_id as expertId, invitation.expert_user_id as expertUserId,
                               invitation.expert_user_uuid as expertUserUuid, roster.expert_name as expertName,
                               invitation.email, invitation.status as invitationStatus,
                               invitation.token_hash as tokenHash,
                               invitation.token_expires_at as tokenExpiresAt,
                               invitation.qr_expires_at as qrExpiresAt,
                               invitation.checked_in_at as checkedInAt,
                               invitation.sent_at as sentAt,
                               invitation.failure_reason as failureReason
                          from competition_review_invitation invitation
                          join competition_review_batch batch
                            on batch.id = invitation.batch_id and batch.deleted = 0
                          join competition_review_roster roster
                            on roster.id = invitation.roster_id
                           and roster.batch_id = invitation.batch_id
                           and roster.expert_id = invitation.expert_id
                           and roster.status = 'SELECTED'
                           and roster.deleted = 0
                         where %s and invitation.deleted = 0
                         limit 1
                        """).formatted(predicate),
                (row, rowNum) -> new InvitationContext(
                        row.getLong("invitationId"),
                        row.getLong("batchId"),
                        row.getString("batchName"),
                        row.getLong("rosterId"),
                        row.getLong("expertId"),
                        row.getObject("expertUserId", Long.class),
                        row.getString("expertUserUuid"),
                        row.getString("expertName"),
                        row.getString("email"),
                        row.getString("invitationStatus"),
                        row.getString("tokenHash"),
                        row.getObject("tokenExpiresAt", LocalDateTime.class),
                        row.getObject("qrExpiresAt", LocalDateTime.class),
                        row.getObject("checkedInAt", LocalDateTime.class),
                        row.getObject("sentAt", LocalDateTime.class),
                        row.getString("failureReason")
                ),
                value
        ).stream().findFirst();
    }

    @Override
    public int issueInvitationQr(
            Long invitationId,
            String qrTokenHash,
            LocalDateTime qrExpiresAt,
            LocalDateTime openedAt
    ) {
        return database.update(
                """
                        update competition_review_invitation
                           set status = 'QR_ISSUED', opened_at = ?, qr_token_hash = ?,
                               qr_expires_at = ?, updated_at = ?
                         where id = ? and status in ('PENDING', 'SENT', 'OPENED', 'QR_ISSUED')
                           and token_expires_at > ? and deleted = 0
                        """,
                openedAt,
                qrTokenHash,
                qrExpiresAt,
                openedAt,
                invitationId,
                openedAt
        );
    }

    @Override
    public int markInvitationCheckedIn(
            Long invitationId,
            Long operatorId,
            String operatorUuid,
            LocalDateTime checkedInAt
    ) {
        return database.update(
                """
                        update competition_review_invitation
                           set status = 'CHECKED_IN', qr_used_at = ?, checked_in_at = ?,
                               checked_in_by = ?, checked_in_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'QR_ISSUED' and qr_expires_at > ? and deleted = 0
                        """,
                checkedInAt,
                checkedInAt,
                operatorId,
                operatorUuid,
                checkedInAt,
                invitationId,
                checkedInAt
        );
    }

    @Override
    public int recordCheckinAttempt(
            Long batchId,
            Long invitationId,
            Long expertId,
            String qrTokenHash,
            String status,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime attemptedAt
    ) {
        return database.update(
                """
                        insert into competition_review_checkin_audit (
                            batch_id, invitation_id, expert_id, qr_token_hash, status, reason,
                            checked_in_by, checked_in_by_uuid, attempted_at,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                batchId,
                invitationId,
                expertId,
                qrTokenHash,
                status,
                reason,
                operatorId,
                operatorUuid,
                attemptedAt,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
    }

    @Override
    public int markBatchAssignmentsConfirmed(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime confirmedAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set assignment_confirmed_at = ?, version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'ASSIGNING' and version = ? and deleted = 0
                        """,
                confirmedAt,
                operatorId,
                operatorUuid,
                confirmedAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public List<ExpertWorkload> listApprovedExpertWorkloads() {
        return database.query(
                """
                        select assignment.expert_id as expertId, count(assignment.id) as activeAssignmentCount
                          from competition_review_assignment assignment
                         where assignment.status in ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS')
                           and assignment.deleted = 0
                         group by assignment.expert_id
                         order by activeAssignmentCount asc, assignment.expert_id asc
                        """,
                (row, rowNum) -> new ExpertWorkload(
                        row.getLong("expertId"),
                        row.getInt("activeAssignmentCount")
                )
        );
    }

    @Override
    public Long insertBatch(
            ReviewVO.Plan plan,
            String batchNo,
            ReviewDTO.BatchCreateRequest request,
            String assignmentStrategy,
            Long operatorId,
            String operatorUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_batch (
                            plan_id, competition_id, stage_id, criteria_version_id,
                            batch_no, batch_name, batch_type, status, assignment_strategy,
                            minimum_reviewer_count, reviewer_count_per_candidate,
                            expert_min_assignments, expert_target_assignments, expert_max_assignments,
                            candidate_count, review_deadline, version,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'STANDARD', 'DRAFT', ?, ?, ?, ?, ?, ?, 0, ?, 1, ?, ?, ?, ?, 0)
                        """,
                plan.getId(),
                plan.getCompetitionId(),
                plan.getStageId(),
                plan.getCriteriaVersionId(),
                batchNo,
                request.getBatchName().trim(),
                assignmentStrategy,
                plan.getRequiredReviewerCount(),
                request.getReviewerCountPerCandidate() == null ? 3 : request.getReviewerCountPerCandidate(),
                request.getExpertMinAssignments() == null ? 5 : request.getExpertMinAssignments(),
                request.getExpertTargetAssignments() == null ? 6 : request.getExpertTargetAssignments(),
                request.getExpertMaxAssignments() == null ? 6 : request.getExpertMaxAssignments(),
                request.getReviewDeadline(),
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public List<CandidateSnapshot> loadCandidateSnapshots(Long competitionId, List<Long> registrationIds) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(competitionId);
        StringBuilder registrationFilter = new StringBuilder();
        if (registrationIds != null && !registrationIds.isEmpty()) {
            registrationFilter.append(" and registration.id in (");
            for (int index = 0; index < registrationIds.size(); index += 1) {
                if (index > 0) {
                    registrationFilter.append(',');
                }
                registrationFilter.append('?');
                parameters.add(registrationIds.get(index));
            }
            registrationFilter.append(')');
        }
        return database.query(
                """
                        select registration.id as registrationId,
                               registration.registration_no as registrationNo,
                               registration.competition_id as competitionId,
                               registration.team_id as teamId,
                               registration.project_id as projectId,
                               registration.owner_user_id as ownerUserId,
                               registration.owner_user_uuid as ownerUserUuid,
                               registration.status,
                               registration.registration_snapshot_json as registrationSnapshotJson,
                               registration.team_snapshot_json as teamSnapshotJson,
                               registration.project_snapshot_json as projectSnapshotJson,
                               registration.member_snapshot_json as memberSnapshotJson,
                               registration.collection_schema_snapshot_json as collectionSchemaSnapshotJson,
                               coalesce((
                                   select json_arrayagg(json_object(
                                       'submissionId', submission.id,
                                       'stageId', submission.stage_id,
                                       'submissionStatus', submission.status,
                                       'submittedAt', submission.submitted_at,
                                       'fieldKey', material_value.field_key,
                                       'fieldType', material_value.field_type,
                                       'textValue', material_value.text_value,
                                       'fileId', material_value.file_id,
                                       'jsonValue', material_value.json_value
                                   ))
                                     from registration_material_submission submission
                                     left join registration_material_value material_value
                                       on material_value.submission_id = submission.id
                                      and material_value.deleted = 0
                                    where submission.registration_id = registration.id
                                      and submission.deleted = 0
                               ), json_array()) as materialSnapshotJson
                          from competition_registration registration
                          join competition_registration_dataset_row dataset_row
                            on dataset_row.registration_id = registration.id
                           and dataset_row.deleted = 0
                          join competition_registration_dataset dataset
                            on dataset.id = dataset_row.dataset_id
                           and dataset.competition_id = registration.competition_id
                           and dataset.status = 'ENABLED'
                           and dataset.deleted = 0
                         where registration.competition_id = ?
                           and registration.status in ('PAID', 'CONFIRMED')
                           and registration.deleted = 0
                        """ + registrationFilter + " order by registration.created_at asc, registration.id asc",
                (row, rowNum) -> new CandidateSnapshot(
                        row.getLong("registrationId"),
                        row.getString("registrationNo"),
                        row.getLong("competitionId"),
                        row.getLong("teamId"),
                        row.getLong("projectId"),
                        row.getLong("ownerUserId"),
                        row.getString("ownerUserUuid"),
                        row.getString("status"),
                        row.getString("registrationSnapshotJson"),
                        row.getString("teamSnapshotJson"),
                        row.getString("projectSnapshotJson"),
                        row.getString("memberSnapshotJson"),
                        row.getString("collectionSchemaSnapshotJson"),
                        row.getString("materialSnapshotJson")
                ),
                parameters.toArray()
        );
    }

    @Override
    public Long insertCandidate(
            Long batchId,
            Long registrationId,
            String blindCode,
            String snapshotJson,
            String reviewSnapshotJson,
            String snapshotHash,
            Long operatorId,
            String operatorUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_candidate (
                            batch_id, registration_id, blind_code, snapshot_json, review_snapshot_json,
                            snapshot_hash, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'FROZEN', ?, ?, ?, ?, 0)
                        """,
                batchId,
                registrationId,
                blindCode,
                snapshotJson,
                reviewSnapshotJson,
                snapshotHash,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public int markBatchReady(
            Long batchId,
            String freezeToken,
            int candidateCount,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime frozenAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set status = 'READY', freeze_token = ?, frozen_at = ?, candidate_count = ?,
                               version = version + 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'DRAFT' and version = ? and deleted = 0
                        """,
                freezeToken,
                frozenAt,
                candidateCount,
                operatorId,
                operatorUuid,
                frozenAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public Optional<AssignmentTarget> findAssignmentTarget(Long batchId, Long candidateId, Long expertId) {
        return database.query(
                """
                        select candidate.id as candidateId,
                               candidate.registration_id as registrationId,
                               candidate.status as candidateStatus,
                               cast(json_unquote(json_extract(candidate.snapshot_json, '$.ownerUserId')) as unsigned)
                                   as candidateOwnerUserId,
                               json_unquote(json_extract(candidate.snapshot_json, '$.ownerUserUuid'))
                                   as candidateOwnerUserUuid,
                               ? as expertId,
                               null as expertUserId,
                               null as expertUserUuid,
                               null as expertStatus,
                               null as approvalStatus,
                               null as accountStatus,
                               0 as identityConflict,
                               candidate.snapshot_json as candidateSnapshotJson
                          from competition_review_candidate candidate
                         where candidate.batch_id = ?
                           and candidate.id = ?
                           and candidate.deleted = 0
                         limit 1
                        """,
                (row, rowNum) -> new AssignmentTarget(
                        row.getLong("candidateId"),
                        row.getLong("registrationId"),
                        row.getString("candidateStatus"),
                        row.getObject("candidateOwnerUserId", Long.class),
                        row.getString("candidateOwnerUserUuid"),
                        row.getLong("expertId"),
                        row.getObject("expertUserId", Long.class),
                        row.getString("expertUserUuid"),
                        row.getString("expertStatus"),
                        row.getString("approvalStatus"),
                        row.getString("accountStatus"),
                        row.getBoolean("identityConflict"),
                        row.getString("candidateSnapshotJson")
                ),
                expertId,
                batchId,
                candidateId
        ).stream().findFirst();
    }

    @Override
    public boolean assignmentExists(Long candidateId, Long expertId) {
        return database.exists(
                """
                        select 1
                          from competition_review_assignment
                         where candidate_id = ? and expert_id = ? and deleted = 0
                         limit 1
                        """,
                candidateId,
                expertId
        );
    }

    @Override
    public Long insertAssignment(
            Long batchId,
            AssignmentTarget target,
            BigDecimal reviewerWeight,
            LocalDateTime dueAt,
            Long operatorId,
            String operatorUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_assignment (
                            batch_id, candidate_id, expert_id, expert_user_id, expert_user_uuid,
                            reviewer_weight, status, due_at, version,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'ASSIGNED', ?, 1, ?, ?, ?, ?, 0)
                        """,
                batchId,
                target.candidateId(),
                target.expertId(),
                target.expertUserId(),
                target.expertUserUuid(),
                reviewerWeight,
                dueAt,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public int markBatchAssigning(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set status = 'ASSIGNING', version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'READY' and version = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public int countCandidatesBelowMinimumAssignments(Long batchId, int minimumReviewerCount) {
        Long count = database.queryForObject(
                """
                        select count(1)
                          from competition_review_candidate candidate
                         where candidate.batch_id = ?
                           and candidate.status = 'FROZEN'
                           and candidate.deleted = 0
                           and (
                               select count(1)
                                 from competition_review_assignment assignment
                                where assignment.candidate_id = candidate.id
                                  and assignment.batch_id = candidate.batch_id
                                  and assignment.status not in ('DECLINED', 'EXPIRED', 'REVOKED')
                                  and assignment.deleted = 0
                           ) < ?
                        """,
                Long.class,
                batchId,
                minimumReviewerCount
        );
        return count == null ? 0 : Math.toIntExact(count);
    }

    @Override
    public int markBatchInReview(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set status = 'IN_REVIEW', version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'ASSIGNING' and version = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public List<ReviewVO.AssignmentTask> listOwnedAssignments(Long userId, String userUuid) {
        List<ReviewVO.AssignmentTask> tasks = database.query(
                """
                        select assignment.id as assignmentId,
                               assignment.batch_id as batchId,
                               batch.batch_name as batchName,
                               assignment.candidate_id as candidateId,
                               candidate.blind_code as blindCode,
                               candidate.review_snapshot_json as candidateSnapshotJson,
                               assignment.status as assignmentStatus,
                               batch.criteria_version_id as criteriaVersionId,
                               plan.score_scale as scoreScale,
                               assignment.due_at as dueAt,
                               assignment.accepted_at as acceptedAt,
                               assignment.submitted_at as submittedAt,
                               assignment.version as assignmentVersion,
                               sheet.id as latestSheetId,
                               sheet.version_no as latestSheetVersion,
                               sheet.status as latestSheetStatus,
                               sheet.total_score as latestTotalScore,
                               sheet.review_comment as latestReviewComment
                          from competition_review_assignment assignment
                          join competition_review_batch batch
                            on batch.id = assignment.batch_id
                           and batch.deleted = 0
                          join competition_review_plan plan
                            on plan.id = batch.plan_id
                           and plan.deleted = 0
                          join competition_review_candidate candidate
                            on candidate.id = assignment.candidate_id
                           and candidate.batch_id = assignment.batch_id
                           and candidate.deleted = 0
                         left join competition_review_sheet sheet
                            on sheet.id = (
                                select latest.id
                                  from competition_review_sheet latest
                                 where latest.assignment_id = assignment.id
                                   and latest.deleted = 0
                                 order by latest.version_no desc, latest.id desc
                                 limit 1
                            )
                         where assignment.expert_user_id = ?
                           and assignment.expert_user_uuid = ?
                           and (
                               not exists (
                                   select 1 from competition_review_roster roster
                                    where roster.batch_id = assignment.batch_id
                                      and roster.status = 'SELECTED' and roster.deleted = 0
                               )
                               or exists (
                                   select 1 from competition_review_invitation checked_invitation
                                    where checked_invitation.batch_id = assignment.batch_id
                                      and checked_invitation.expert_id = assignment.expert_id
                                      and checked_invitation.status = 'CHECKED_IN'
                                      and checked_invitation.checked_in_at is not null
                                      and checked_invitation.deleted = 0
                               )
                           )
                           and assignment.deleted = 0
                         order by assignment.due_at is null, assignment.due_at asc, assignment.id desc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.AssignmentTask.class),
                userId,
                userUuid
        );
        for (ReviewVO.AssignmentTask task : tasks) {
            task.setCriteria(
                    task.getCriteriaVersionId() == null
                            ? List.of()
                            : listCriteria(task.getCriteriaVersionId())
            );
            task.setLatestScores(
                    task.getLatestSheetId() == null
                            ? List.of()
                            : listScoreItems(task.getLatestSheetId())
            );
        }
        return tasks;
    }

    @Override
    public Optional<AssignmentContext> findOwnedAssignment(Long assignmentId, Long userId, String userUuid) {
        Optional<AssignmentContext> assignment = database.query(
                """
                        select assignment.id as assignmentId,
                               assignment.batch_id as batchId,
                               batch.status as batchStatus,
                               assignment.candidate_id as candidateId,
                               assignment.expert_id as expertId,
                               assignment.status as assignmentStatus,
                               assignment.version as assignmentVersion,
                               batch.criteria_version_id as criteriaVersionId,
                               plan.score_scale as scoreScale,
                               assignment.due_at as dueAt
                          from competition_review_assignment assignment
                          join competition_review_batch batch
                            on batch.id = assignment.batch_id
                           and batch.deleted = 0
                          join competition_review_plan plan
                            on plan.id = batch.plan_id
                           and plan.deleted = 0
                         where assignment.id = ?
                           and assignment.expert_user_id = ?
                           and assignment.expert_user_uuid = ?
                           and (
                               not exists (
                                   select 1 from competition_review_roster roster
                                    where roster.batch_id = assignment.batch_id
                                      and roster.status = 'SELECTED' and roster.deleted = 0
                               )
                               or exists (
                                   select 1 from competition_review_invitation checked_invitation
                                    where checked_invitation.batch_id = assignment.batch_id
                                      and checked_invitation.expert_id = assignment.expert_id
                                      and checked_invitation.status = 'CHECKED_IN'
                                      and checked_invitation.checked_in_at is not null
                                      and checked_invitation.deleted = 0
                               )
                           )
                           and assignment.deleted = 0
                         limit 1
                        """,
                (row, rowNum) -> new AssignmentContext(
                        row.getLong("assignmentId"),
                        row.getLong("batchId"),
                        row.getString("batchStatus"),
                        row.getLong("candidateId"),
                        row.getLong("expertId"),
                        row.getString("assignmentStatus"),
                        row.getInt("assignmentVersion"),
                        row.getLong("criteriaVersionId"),
                        row.getBigDecimal("scoreScale"),
                        row.getObject("dueAt", LocalDateTime.class),
                        List.of()
                ),
                assignmentId,
                userId,
                userUuid
        ).stream().findFirst();
        if (assignment.isEmpty()) {
            return Optional.empty();
        }
        AssignmentContext value = assignment.get();
        return Optional.of(new AssignmentContext(
                value.assignmentId(),
                value.batchId(),
                value.batchStatus(),
                value.candidateId(),
                value.expertId(),
                value.assignmentStatus(),
                value.assignmentVersion(),
                value.criteriaVersionId(),
                value.scoreScale(),
                value.dueAt(),
                listCriteria(value.criteriaVersionId())
        ));
    }

    @Override
    public int acceptAssignment(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            LocalDateTime acceptedAt
    ) {
        return database.update(
                """
                        update competition_review_assignment
                           set status = 'ACCEPTED', accepted_at = ?, version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ?
                           and expert_user_id = ?
                           and expert_user_uuid = ?
                           and status = 'ASSIGNED'
                           and version = ?
                           and deleted = 0
                        """,
                acceptedAt,
                userId,
                userUuid,
                acceptedAt,
                assignmentId,
                userId,
                userUuid,
                expectedVersion
        );
    }

    @Override
    public int declineAssignment(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            String reason,
            LocalDateTime declinedAt
    ) {
        return database.update(
                """
                        update competition_review_assignment
                           set status = 'DECLINED', decline_reason = ?, declined_at = ?,
                               version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ?
                           and expert_user_id = ?
                           and expert_user_uuid = ?
                           and status = 'ASSIGNED'
                           and version = ?
                           and deleted = 0
                        """,
                reason,
                declinedAt,
                userId,
                userUuid,
                declinedAt,
                assignmentId,
                userId,
                userUuid,
                expectedVersion
        );
    }

    @Override
    public int revokeAssignment(
            Long batchId,
            Long assignmentId,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime revokedAt
    ) {
        return database.update(
                """
                        update competition_review_assignment
                           set status = 'REVOKED', revoke_reason = ?, revoked_at = ?,
                               version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and batch_id = ?
                           and status in ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS')
                           and deleted = 0
                        """,
                reason,
                revokedAt,
                operatorId,
                operatorUuid,
                revokedAt,
                assignmentId,
                batchId
        );
    }

    @Override
    public int expireDueAssignments(LocalDateTime expiredAt) {
        return database.update(
                """
                        update competition_review_assignment assignment
                          join competition_review_batch batch
                            on batch.id = assignment.batch_id
                           and batch.status in ('ASSIGNING', 'IN_REVIEW')
                           and batch.deleted = 0
                           set assignment.status = 'EXPIRED',
                               assignment.expired_at = ?,
                               assignment.version = assignment.version + 1,
                               assignment.updated_by = 0,
                               assignment.updated_by_uuid = null,
                               assignment.updated_at = ?
                         where assignment.status in ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS')
                           and assignment.due_at is not null
                           and assignment.due_at <= ?
                           and assignment.deleted = 0
                        """,
                expiredAt,
                expiredAt,
                expiredAt
        );
    }

    @Override
    public Integer findLatestSheetVersion(Long assignmentId) {
        Integer version = database.queryForObject(
                """
                        select coalesce(max(version_no), 0)
                          from competition_review_sheet
                         where assignment_id = ? and deleted = 0
                        """,
                Integer.class,
                assignmentId
        );
        return version == null ? 0 : version;
    }

    @Override
    public Long insertSheet(
            AssignmentContext assignment,
            int versionNo,
            String status,
            BigDecimal totalScore,
            String reviewComment,
            LocalDateTime submittedAt,
            Long userId,
            String userUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_sheet (
                            assignment_id, batch_id, candidate_id, expert_id, version_no,
                            status, total_score, review_comment, submitted_at,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                assignment.assignmentId(),
                assignment.batchId(),
                assignment.candidateId(),
                assignment.expertId(),
                versionNo,
                status,
                totalScore,
                reviewComment,
                submittedAt,
                userId,
                userUuid,
                userId,
                userUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public Long insertScoreItem(
            Long sheetId,
            ReviewDTO.ScoreItemRequest score,
            Long userId,
            String userUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_score_item (
                            sheet_id, criterion_id, score, comment,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                sheetId,
                score.getCriterionId(),
                score.getScore(),
                score.getComment(),
                userId,
                userUuid,
                userId,
                userUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public int markAssignmentInProgress(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_assignment
                           set status = 'IN_PROGRESS', version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ?
                           and expert_user_id = ?
                           and expert_user_uuid = ?
                           and status = 'ACCEPTED'
                           and version = ?
                           and deleted = 0
                        """,
                userId,
                userUuid,
                updatedAt,
                assignmentId,
                userId,
                userUuid,
                expectedVersion
        );
    }

    @Override
    public int markAssignmentSubmitted(
            Long assignmentId,
            int expectedVersion,
            Long userId,
            String userUuid,
            LocalDateTime submittedAt
    ) {
        return database.update(
                """
                        update competition_review_assignment
                           set status = 'SUBMITTED', submitted_at = ?, version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ?
                           and expert_user_id = ?
                           and expert_user_uuid = ?
                           and status in ('ACCEPTED', 'IN_PROGRESS')
                           and version = ?
                           and deleted = 0
                        """,
                submittedAt,
                userId,
                userUuid,
                submittedAt,
                assignmentId,
                userId,
                userUuid,
                expectedVersion
        );
    }

    @Override
    public Optional<ReviewVO.ReviewSheet> findSheet(Long sheetId, Long assignmentId) {
        return database.query(
                """
                        select id, assignment_id as assignmentId, batch_id as batchId,
                               candidate_id as candidateId, version_no as versionNo, status,
                               total_score as totalScore, review_comment as reviewComment,
                               submitted_at as submittedAt
                          from competition_review_sheet
                         where id = ? and assignment_id = ? and deleted = 0
                         limit 1
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.ReviewSheet.class),
                sheetId,
                assignmentId
        ).stream().findFirst();
    }

    @Override
    public List<ReviewVO.ScoreItem> listScoreItems(Long sheetId) {
        return database.query(
                """
                        select criterion_id as criterionId, score, comment
                          from competition_review_score_item
                         where sheet_id = ? and deleted = 0
                         order by criterion_id asc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.ScoreItem.class),
                sheetId
        );
    }

    @Override
    public int countCandidatesBelowMinimumSubmitted(Long batchId, int minimumSubmittedCount) {
        Long count = database.queryForObject(
                """
                        select count(1)
                          from competition_review_candidate candidate
                         where candidate.batch_id = ?
                           and candidate.status = 'FROZEN'
                           and candidate.deleted = 0
                           and (
                               select count(distinct sheet.assignment_id)
                                 from competition_review_sheet sheet
                                 join competition_review_assignment assignment
                                   on assignment.id = sheet.assignment_id
                                  and assignment.candidate_id = candidate.id
                                  and assignment.status = 'SUBMITTED'
                                  and assignment.deleted = 0
                                where sheet.batch_id = candidate.batch_id
                                  and sheet.candidate_id = candidate.id
                                  and sheet.status = 'SUBMITTED'
                                  and sheet.deleted = 0
                           ) < ?
                        """,
                Long.class,
                batchId,
                minimumSubmittedCount
        );
        return count == null ? 0 : Math.toIntExact(count);
    }

    @Override
    public int markBatchAggregating(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set status = 'AGGREGATING', version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'IN_REVIEW' and version = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public List<AggregationSource> loadAggregationSources(Long batchId) {
        Map<Long, List<WeightedScore>> scoresByCandidate = new LinkedHashMap<>();
        database.query(
                """
                        select candidate.id as candidateId,
                               case when assignment.id is null then null else sheet.total_score end as totalScore,
                               assignment.reviewer_weight as reviewerWeight
                          from competition_review_candidate candidate
                          left join competition_review_sheet sheet
                            on sheet.batch_id = candidate.batch_id
                           and sheet.candidate_id = candidate.id
                           and sheet.status = 'SUBMITTED'
                           and sheet.deleted = 0
                          left join competition_review_assignment assignment
                            on assignment.id = sheet.assignment_id
                           and assignment.status = 'SUBMITTED'
                           and assignment.deleted = 0
                         where candidate.batch_id = ?
                           and candidate.status = 'FROZEN'
                           and candidate.deleted = 0
                         order by candidate.id asc, sheet.assignment_id asc
                        """,
                (row, rowNum) -> {
                    Long candidateId = row.getLong("candidateId");
                    List<WeightedScore> scores =
                            scoresByCandidate.computeIfAbsent(candidateId, ignored -> new ArrayList<>());
                    BigDecimal score = row.getBigDecimal("totalScore");
                    if (score != null) {
                        BigDecimal reviewerWeight = row.getBigDecimal("reviewerWeight");
                        scores.add(new WeightedScore(
                                score,
                                reviewerWeight == null ? BigDecimal.ONE : reviewerWeight
                        ));
                    }
                    return candidateId;
                },
                batchId
        );
        return scoresByCandidate.entrySet().stream()
                .map(entry -> new AggregationSource(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    @Override
    public int upsertAggregate(
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
    ) {
        return database.update(
                """
                        insert into competition_review_aggregate (
                            batch_id, candidate_id, aggregate_score, minimum_score, maximum_score,
                            score_stddev, submitted_reviewer_count, valid_reviewer_count, rank_no,
                            decision, anomaly_flags_json, status, calculated_at,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, 'CALCULATED', ?,
                                  ?, ?, ?, ?, 0)
                        on duplicate key update
                            aggregate_score = values(aggregate_score),
                            minimum_score = values(minimum_score),
                            maximum_score = values(maximum_score),
                            score_stddev = values(score_stddev),
                            submitted_reviewer_count = values(submitted_reviewer_count),
                            valid_reviewer_count = values(valid_reviewer_count),
                            rank_no = values(rank_no),
                            decision = 'PENDING',
                            decision_reason = null,
                            decided_by = null,
                            decided_by_uuid = null,
                            decided_at = null,
                            anomaly_flags_json = values(anomaly_flags_json),
                            status = 'CALCULATED',
                            calculated_at = values(calculated_at),
                            finalized_at = null,
                            updated_by = values(updated_by),
                            updated_by_uuid = values(updated_by_uuid),
                            updated_at = values(calculated_at)
                        """,
                batchId,
                candidateId,
                aggregateScore,
                minimumScore,
                maximumScore,
                scoreStddev,
                submittedReviewerCount,
                validReviewerCount,
                rankNo,
                anomalyFlagsJson,
                calculatedAt,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
    }

    @Override
    public List<ReviewVO.Aggregate> listAggregates(Long batchId) {
        return database.query(
                """
                        select id, batch_id as batchId, candidate_id as candidateId,
                               aggregate_score as aggregateScore, minimum_score as minimumScore,
                               maximum_score as maximumScore, score_stddev as scoreStddev,
                               submitted_reviewer_count as submittedReviewerCount,
                               valid_reviewer_count as validReviewerCount, rank_no as rankNo,
                               decision, decision_reason as decisionReason,
                               decided_by as decidedBy, decided_by_uuid as decidedByUuid,
                               decided_at as decidedAt,
                               anomaly_flags_json as anomalyFlagsJson, status,
                               calculated_at as calculatedAt, finalized_at as finalizedAt
                          from competition_review_aggregate
                         where batch_id = ? and deleted = 0
                         order by rank_no asc, candidate_id asc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.Aggregate.class),
                batchId
        );
    }

    @Override
    public int updateAggregateDecision(
            Long batchId,
            Long candidateId,
            String decision,
            String decisionReason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_aggregate
                           set decision = ?, decision_reason = ?,
                               decided_by = ?, decided_by_uuid = ?, decided_at = ?,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where batch_id = ? and candidate_id = ?
                           and status = 'CALCULATED' and deleted = 0
                        """,
                decision,
                decisionReason,
                operatorId,
                operatorUuid,
                updatedAt,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId,
                candidateId
        );
    }

    @Override
    public int countPendingAggregateDecisions(Long batchId) {
        Long count = database.queryForObject(
                """
                        select count(1)
                          from competition_review_aggregate
                         where batch_id = ? and decision = 'PENDING' and deleted = 0
                        """,
                Long.class,
                batchId
        );
        return count == null ? 0 : Math.toIntExact(count);
    }

    @Override
    public int finalizeAggregates(
            Long batchId,
            Long operatorId,
            String operatorUuid,
            LocalDateTime finalizedAt
    ) {
        return database.update(
                """
                        update competition_review_aggregate
                           set status = 'FINALIZED', finalized_at = ?,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where batch_id = ? and status = 'CALCULATED'
                           and decision <> 'PENDING' and deleted = 0
                        """,
                finalizedAt,
                operatorId,
                operatorUuid,
                finalizedAt,
                batchId
        );
    }

    @Override
    public int markBatchFinalized(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime finalizedAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set status = 'FINALIZED', finalized_at = ?, version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'AGGREGATING' and version = ? and deleted = 0
                        """,
                finalizedAt,
                operatorId,
                operatorUuid,
                finalizedAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public List<PublicationRow> loadPublicationRows(Long batchId) {
        return database.query(
                """
                        select candidate.id as candidateId,
                               candidate.registration_id as registrationId,
                               cast(json_unquote(json_extract(candidate.snapshot_json, '$.ownerUserId')) as unsigned) as ownerUserId,
                               json_unquote(json_extract(candidate.snapshot_json, '$.ownerUserUuid')) as ownerUserUuid,
                               candidate.blind_code as blindCode,
                               aggregate.aggregate_score as aggregateScore,
                               aggregate.rank_no as rankNo,
                               aggregate.decision
                          from competition_review_aggregate aggregate
                          join competition_review_candidate candidate
                            on candidate.id = aggregate.candidate_id
                           and candidate.batch_id = aggregate.batch_id
                           and candidate.deleted = 0
                         where aggregate.batch_id = ?
                           and aggregate.status = 'FINALIZED'
                           and aggregate.deleted = 0
                         order by aggregate.rank_no asc, candidate.id asc
                        """,
                (row, rowNum) -> new PublicationRow(
                        row.getLong("candidateId"),
                        row.getLong("registrationId"),
                        row.getLong("ownerUserId"),
                        row.getString("ownerUserUuid"),
                        row.getString("blindCode"),
                        row.getBigDecimal("aggregateScore"),
                        row.getObject("rankNo", Integer.class),
                        row.getString("decision")
                ),
                batchId
        );
    }

    @Override
    public Integer findLatestPublicationVersion(Long batchId) {
        Integer version = database.queryForObject(
                """
                        select coalesce(max(publication_version), 0)
                          from competition_review_publication
                         where batch_id = ? and deleted = 0
                        """,
                Integer.class,
                batchId
        );
        return version == null ? 0 : version;
    }

    @Override
    public Long insertPublication(
            Long batchId,
            int publicationVersion,
            String payloadJson,
            String payloadHash,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_publication (
                            batch_id, publication_version, status, payload_json, payload_hash,
                            published_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, 'PUBLISHED', ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                batchId,
                publicationVersion,
                payloadJson,
                payloadHash,
                publishedAt,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public int projectLegacyResult(
            ReviewVO.Batch batch,
            PublicationRow result,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    ) {
        return database.update(
                """
                        insert into competition_stage_review_result (
                            competition_id, stage_id, registration_id, score, decision,
                            published_at, decided_by, decided_by_uuid,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            score = values(score),
                            decision = values(decision),
                            published_at = values(published_at),
                            decided_by = values(decided_by),
                            decided_by_uuid = values(decided_by_uuid),
                            updated_by = values(updated_by),
                            updated_by_uuid = values(updated_by_uuid),
                            updated_at = values(published_at)
                        """,
                batch.getCompetitionId(),
                batch.getStageId(),
                result.registrationId(),
                result.aggregateScore(),
                result.decision(),
                publishedAt,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
    }

    @Override
    public int markBatchPublished(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime publishedAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set status = 'PUBLISHED', published_at = ?, version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'FINALIZED' and version = ? and deleted = 0
                        """,
                publishedAt,
                operatorId,
                operatorUuid,
                publishedAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public Optional<ReviewVO.Publication> findLatestPublication(Long batchId) {
        return database.query(
                """
                        select id, batch_id as batchId, publication_version as publicationVersion,
                               status, payload_json as payloadJson, payload_hash as payloadHash,
                               published_at as publishedAt, revoked_at as revokedAt,
                               revoke_reason as revokeReason
                          from competition_review_publication
                         where batch_id = ? and status = 'PUBLISHED' and deleted = 0
                         order by publication_version desc, id desc
                         limit 1
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.Publication.class),
                batchId
        ).stream().findFirst();
    }

    @Override
    public int revokeLatestPublication(
            Long batchId,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime revokedAt
    ) {
        return database.update(
                """
                        update competition_review_publication
                           set status = 'REVOKED', revoked_at = ?, revoke_reason = ?,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where batch_id = ? and status = 'PUBLISHED' and deleted = 0
                         order by publication_version desc, id desc
                         limit 1
                        """,
                revokedAt,
                reason,
                operatorId,
                operatorUuid,
                revokedAt,
                batchId
        );
    }

    @Override
    public int reopenFinalizedAggregates(
            Long batchId,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_aggregate
                           set status = 'CALCULATED', finalized_at = null,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where batch_id = ? and status = 'FINALIZED' and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId
        );
    }

    @Override
    public int markLegacyProjectionCorrectionPending(
            ReviewVO.Batch batch,
            String reason,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_stage_review_result
                           set decision = 'PENDING', review_comment = ?,
                               published_at = null, updated_by = ?,
                               updated_by_uuid = ?, updated_at = ?
                         where competition_id = ? and stage_id = ? and deleted = 0
                        """,
                reason,
                operatorId,
                operatorUuid,
                updatedAt,
                batch.getCompetitionId(),
                batch.getStageId()
        );
    }

    @Override
    public int reopenPublishedBatch(
            Long batchId,
            int expectedVersion,
            Long operatorId,
            String operatorUuid,
            LocalDateTime updatedAt
    ) {
        return database.update(
                """
                        update competition_review_batch
                           set status = 'AGGREGATING', finalized_at = null,
                               published_at = null, version = version + 1,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'PUBLISHED'
                           and version = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                updatedAt,
                batchId,
                expectedVersion
        );
    }

    @Override
    public List<ReviewVO.PublishedResult> listOwnedPublishedResults(
            Long userId,
            String userUuid,
            List<Long> activeTeamIds
    ) {
        return database.query(
                """
                        select publication.id as publicationId,
                               publication.publication_version as publicationVersion,
                               batch.id as batchId, batch.competition_id as competitionId,
                               batch.stage_id as stageId, candidate.id as candidateId,
                               competition.title as competitionTitle,
                               stage.stage_name as stageName,
                               registration.id as registrationId,
                               registration.registration_no as registrationNo,
                               review_aggregate.aggregate_score as aggregateScore,
                               review_aggregate.rank_no as rankNo, review_aggregate.decision,
                               publication.published_at as publishedAt,
                               appeal.id as appealId, appeal.status as appealStatus
                          from competition_review_publication publication
                          join competition_review_batch batch
                            on batch.id = publication.batch_id
                           and batch.status = 'PUBLISHED' and batch.deleted = 0
                          join competition_review_candidate candidate
                            on candidate.batch_id = batch.id and candidate.deleted = 0
                           join competition_registration registration
                             on registration.id = candidate.registration_id and registration.deleted = 0
                           join aiadc_competition competition
                             on competition.id = batch.competition_id and competition.deleted = 0
                           join competition_stage stage
                             on stage.id = batch.stage_id
                            and stage.competition_id = batch.competition_id
                            and stage.deleted = 0
                           join competition_review_aggregate review_aggregate
                            on review_aggregate.batch_id = batch.id
                           and review_aggregate.candidate_id = candidate.id
                           and review_aggregate.status = 'FINALIZED'
                           and review_aggregate.deleted = 0
                          left join competition_review_appeal appeal
                            on appeal.publication_id = publication.id
                           and appeal.candidate_id = candidate.id
                           and appeal.registration_id = registration.id
                           and appeal.deleted = 0
                         where publication.status = 'PUBLISHED' and publication.deleted = 0
                           and publication.publication_version = (
                               select max(latest.publication_version)
                                 from competition_review_publication latest
                                where latest.batch_id = publication.batch_id
                                  and latest.status = 'PUBLISHED' and latest.deleted = 0
                           )
                           and (
                               (registration.owner_user_id = ? and registration.owner_user_uuid = ?)
                               or find_in_set(cast(registration.team_id as char), ?) > 0
                           )
                         order by publication.published_at desc, registration.id desc
                        """,
                new BeanPropertyRowMapper<>(ReviewVO.PublishedResult.class),
                userId,
                userUuid,
                teamIdCsv(activeTeamIds)
        );
    }

    @Override
    public Optional<AppealTarget> findOwnedAppealTarget(
            Long publicationId,
            Long registrationId,
            Long userId,
            String userUuid,
            List<Long> activeTeamIds
    ) {
        return database.query(
                """
                        select publication.id as publicationId,
                               publication.publication_version as publicationVersion,
                               batch.id as batchId, batch.competition_id as competitionId,
                               batch.stage_id as stageId, candidate.id as candidateId,
                               registration.id as registrationId,
                               review_aggregate.aggregate_score as aggregateScore,
                               review_aggregate.rank_no as rankNo, review_aggregate.decision,
                               publication.published_at as publishedAt
                          from competition_review_publication publication
                          join competition_review_batch batch
                            on batch.id = publication.batch_id
                           and batch.status = 'PUBLISHED' and batch.deleted = 0
                          join competition_review_candidate candidate
                            on candidate.batch_id = batch.id and candidate.deleted = 0
                          join competition_registration registration
                            on registration.id = candidate.registration_id and registration.deleted = 0
                          join competition_review_aggregate review_aggregate
                            on review_aggregate.batch_id = batch.id
                           and review_aggregate.candidate_id = candidate.id
                           and review_aggregate.status = 'FINALIZED'
                           and review_aggregate.deleted = 0
                         where publication.id = ? and publication.status = 'PUBLISHED'
                           and publication.deleted = 0 and registration.id = ?
                           and (
                               (registration.owner_user_id = ? and registration.owner_user_uuid = ?)
                               or find_in_set(cast(registration.team_id as char), ?) > 0
                           )
                         limit 1
                        """,
                (row, rowNum) -> new AppealTarget(
                        row.getLong("publicationId"),
                        row.getInt("publicationVersion"),
                        row.getLong("batchId"),
                        row.getLong("competitionId"),
                        row.getLong("stageId"),
                        row.getLong("candidateId"),
                        row.getLong("registrationId"),
                        row.getBigDecimal("aggregateScore"),
                        row.getObject("rankNo", Integer.class),
                        row.getString("decision"),
                        row.getObject("publishedAt", LocalDateTime.class)
                ),
                publicationId,
                registrationId,
                userId,
                userUuid,
                teamIdCsv(activeTeamIds)
        ).stream().findFirst();
    }

    @Override
    public Optional<ReviewVO.Appeal> findAppealByPublicationAndRegistration(
            Long publicationId,
            Long registrationId
    ) {
        return database.query(
                APPEAL_SELECT
                        + " where appeal.publication_id = ? and appeal.registration_id = ?"
                        + " and appeal.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ReviewVO.Appeal.class),
                publicationId,
                registrationId
        ).stream().findFirst();
    }

    @Override
    public Optional<ReviewVO.Appeal> findAppeal(Long appealId) {
        return database.query(
                APPEAL_SELECT + " where appeal.id = ? and appeal.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ReviewVO.Appeal.class),
                appealId
        ).stream().findFirst();
    }

    @Override
    public Long insertAppeal(
            AppealTarget target,
            String appealNo,
            String reason,
            Long operatorId,
            String operatorUuid
    ) {
        int inserted = database.update(
                """
                        insert into competition_review_appeal (
                            publication_id, candidate_id, registration_id, appeal_no,
                            appeal_reason, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, 0)
                        """,
                target.publicationId(),
                target.candidateId(),
                target.registrationId(),
                appealNo,
                reason,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        return inserted == 1 ? lastInsertId() : null;
    }

    @Override
    public List<ReviewVO.Appeal> listOwnedAppeals(Long userId, String userUuid) {
        return database.query(
                APPEAL_SELECT
                        + " where appeal.created_by = ? and appeal.created_by_uuid = ?"
                        + " and appeal.deleted = 0 order by appeal.created_at desc, appeal.id desc",
                new BeanPropertyRowMapper<>(ReviewVO.Appeal.class),
                userId,
                userUuid
        );
    }

    @Override
    public List<ReviewVO.Appeal> listAppeals(Long batchId, String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where appeal.deleted = 0");
        if (batchId != null) {
            where.append(" and publication.batch_id = ?");
            args.add(batchId);
        }
        if (status != null) {
            where.append(" and appeal.status = ?");
            args.add(status);
        }
        return database.query(
                APPEAL_SELECT + where + " order by appeal.created_at desc, appeal.id desc",
                new BeanPropertyRowMapper<>(ReviewVO.Appeal.class),
                args.toArray()
        );
    }

    @Override
    public int resolveAppeal(
            Long appealId,
            String decision,
            String resolution,
            Long operatorId,
            String operatorUuid,
            LocalDateTime resolvedAt
    ) {
        return database.update(
                """
                        update competition_review_appeal
                           set status = ?, resolution = ?, resolved_by = ?,
                               resolved_by_uuid = ?, resolved_at = ?,
                               updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where id = ? and status = 'SUBMITTED' and deleted = 0
                        """,
                decision,
                resolution,
                operatorId,
                operatorUuid,
                resolvedAt,
                operatorId,
                operatorUuid,
                resolvedAt,
                appealId
        );
    }

    private static String teamIdCsv(List<Long> teamIds) {
        return teamIds == null ? "" : teamIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(teamId -> teamId > 0)
                .distinct()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private Long lastInsertId() {
        return database.queryForObject("select last_insert_id()", Long.class);
    }
}
