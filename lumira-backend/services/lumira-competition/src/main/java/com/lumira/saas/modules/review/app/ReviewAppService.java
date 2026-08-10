package com.lumira.saas.modules.review.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.PlatformEventPort;
import com.lumira.api.expert.ExpertSnapshot;
import com.lumira.api.expert.ExpertSnapshotPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.registration.api.RegistrationCandidateSnapshotDTO;
import com.lumira.registration.api.RegistrationReviewInternalApi;
import com.lumira.review.api.ReviewIntegrationEvents;
import com.lumira.review.api.ReviewNotificationPort;
import com.lumira.saas.modules.review.dto.ReviewDTO;
import com.lumira.saas.modules.review.domain.ReviewBatchStatus;
import com.lumira.saas.modules.review.repository.ReviewRepository;
import com.lumira.saas.modules.review.vo.ReviewVO;
import com.lumira.team.api.TeamInternalApi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ReviewAppService {

    public static final String PLAN_MANAGE = "review:plan:manage";
    public static final String BATCH_CREATE = "review:batch:create";
    public static final String ASSIGNMENT_MANAGE = "review:assignment:manage";
    public static final String ROSTER_MANAGE = "review:roster:manage";
    public static final String NOTIFICATION_SEND = "review:notification:send";
    public static final String CHECKIN_SCAN = "review:checkin:scan";
    public static final String TASK_VIEW = "review:task:view";
    public static final String SCORE_SUBMIT = "review:score:submit";
    public static final String RESULT_AGGREGATE = "review:result:aggregate";
    public static final String RESULT_FINALIZE = "review:result:finalize";
    public static final String RESULT_PUBLISH = "review:result:publish";
    public static final String APPEAL_SUBMIT = "review:appeal:submit";
    public static final String APPEAL_MANAGE = "review:appeal:manage";
    public static final String RESULT_PUBLISHED_EVENT = ReviewIntegrationEvents.RESULT_PUBLISHED;
    private static final Set<String> BLIND_MODES = Set.of("NONE", "SINGLE_BLIND", "DOUBLE_BLIND");
    private static final Set<String> AGGREGATE_METHODS =
            Set.of("AVERAGE", "MEDIAN", "WEIGHTED_AVERAGE", "TRIMMED_MEAN");
    private static final Set<String> ASSIGNMENT_STRATEGIES = Set.of("MANUAL", "ROUND_ROBIN", "BALANCED", "TAG_MATCH");
    private static final Set<String> REVIEW_DECISIONS = Set.of(
            "PASS", "FAIL", "WAITLIST", "ADVANCED", "ELIMINATED", "REVIEW_REQUIRED"
    );
    private static final Set<String> APPEAL_DECISIONS = Set.of("ACCEPTED", "REJECTED");
    private static final Set<String> BLIND_SENSITIVE_KEYS = Set.of(
            "registrationno",
            "owner",
            "ownername",
            "owneruserid",
            "owneruseruuid",
            "userid",
            "useruuid",
            "username",
            "membername",
            "teamname",
            "leadername",
            "responsibleperson",
            "rightsholder",
            "holdername",
            "recipientname",
            "contact",
            "contactname",
            "contactemail",
            "contactphone",
            "email",
            "mobile",
            "phone",
            "telephone",
            "idcard",
            "identityno",
            "employeeno",
            "studentno",
            "originalfilename",
            "filename",
            "uploadername",
            "createdby",
            "updatedby"
    );
    private static final Pattern BLIND_EMAIL_PATTERN = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"
    );
    private static final Pattern BLIND_MOBILE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final DateTimeFormatter BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int DEFAULT_REVIEWER_COUNT_PER_CANDIDATE = 3;
    private static final int DEFAULT_EXPERT_MIN_ASSIGNMENTS = 5;
    private static final int DEFAULT_EXPERT_TARGET_ASSIGNMENTS = 6;
    private static final int DEFAULT_EXPERT_MAX_ASSIGNMENTS = 6;
    private static final int INVITATION_TTL_HOURS = 24;
    private static final int CHECKIN_QR_TTL_MINUTES = 5;

    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;
    private PlatformEventPort platformEventPublisher;
    private RegistrationReviewInternalApi registrationReviewInternalApi;
    private ObjectProvider<ExpertSnapshotPort> expertSnapshotPortProvider;
    private ObjectProvider<TeamInternalApi> teamInternalApiProvider;
    private ObjectProvider<ReviewNotificationPort> reviewNotificationPortProvider;
    private String reviewInvitationUrl = "http://localhost:8000/review/invitation";
    private Counter publicationCounter;
    private Counter publishedResultCounter;
    private Counter appealSubmittedCounter;

    public ReviewAppService(ReviewRepository reviewRepository, ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setPlatformEventPublisher(PlatformEventPort platformEventPublisher) {
        this.platformEventPublisher = platformEventPublisher;
    }

    @Autowired
    public void setRegistrationReviewInternalApi(
            RegistrationReviewInternalApi registrationReviewInternalApi
    ) {
        this.registrationReviewInternalApi = registrationReviewInternalApi;
    }

    @Autowired
    public void setExpertSnapshotPortProvider(ObjectProvider<ExpertSnapshotPort> expertSnapshotPortProvider) {
        this.expertSnapshotPortProvider = expertSnapshotPortProvider;
    }

    @Autowired
    public void setTeamInternalApiProvider(ObjectProvider<TeamInternalApi> teamInternalApiProvider) {
        this.teamInternalApiProvider = teamInternalApiProvider;
    }

    @Autowired
    public void setReviewNotificationPortProvider(ObjectProvider<ReviewNotificationPort> reviewNotificationPortProvider) {
        this.reviewNotificationPortProvider = reviewNotificationPortProvider;
    }

    @Autowired(required = false)
    public void setReviewInvitationUrl(
            @Value("${lumira.review.invitation-url:http://localhost:8000/review/invitation}") String reviewInvitationUrl
    ) {
        if (StringUtils.hasText(reviewInvitationUrl)) {
            this.reviewInvitationUrl = reviewInvitationUrl.trim();
        }
    }

    @Autowired
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        publicationCounter = Counter.builder("competition.review.publication")
                .register(meterRegistry);
        publishedResultCounter = Counter.builder("competition.review.result.published")
                .register(meterRegistry);
        appealSubmittedCounter = Counter.builder("competition.review.appeal.submitted")
                .register(meterRegistry);
    }

    @Transactional
    public ReviewVO.Plan createPlan(CurrentUser currentUser, ReviewDTO.PlanCreateRequest request) {
        Operator operator = requirePermission(currentUser, PLAN_MANAGE);
        if (request == null || request.getCompetitionId() == null || request.getCompetitionId() <= 0
                || request.getStageId() == null || request.getStageId() <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Competition and stage are required");
        }
        if (!StringUtils.hasText(request.getPlanName())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review plan name is required");
        }
        if (!stageBelongsToCompetition(request.getCompetitionId(), request.getStageId())) {
            throw biz(ErrorCode.NOT_FOUND, "Competition stage not found");
        }
        if (reviewRepository.findPlanByStage(request.getCompetitionId(), request.getStageId()).isPresent()) {
            throw biz(ErrorCode.BIZ_ERROR, "A review plan already exists for this stage");
        }

        List<ReviewDTO.CriterionRequest> criteria = requireValidCriteria(request.getCriteria());
        int requiredReviewers = defaultInt(request.getRequiredReviewerCount(), 1);
        int minimumSubmitted = defaultInt(request.getMinimumSubmittedCount(), requiredReviewers);
        int trimHighest = defaultInt(request.getTrimHighestCount(), 0);
        int trimLowest = defaultInt(request.getTrimLowestCount(), 0);
        if (minimumSubmitted > requiredReviewers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Minimum submitted reviews cannot exceed required reviewers");
        }
        if (trimHighest + trimLowest >= minimumSubmitted) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Trim counts must leave at least one valid review");
        }
        String blindMode = normalizeEnum(request.getBlindMode(), "NONE", BLIND_MODES, "Invalid blind review mode");
        String aggregateMethod = normalizeEnum(
                request.getAggregateMethod(),
                "AVERAGE",
                AGGREGATE_METHODS,
                "Invalid aggregate method"
        );
        BigDecimal scoreScale = request.getScoreScale() == null ? new BigDecimal("100.00") : request.getScoreScale();
        BigDecimal totalWeight = criteria.stream()
                .map(ReviewDTO.CriterionRequest::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Criterion weights must add up to 1");
        }

        Long planId = reviewRepository.insertPlan(
                request,
                blindMode,
                requiredReviewers,
                minimumSubmitted,
                aggregateMethod,
                scoreScale,
                trimHighest,
                trimLowest,
                operator.userId(),
                operator.userUuid()
        );
        requireInserted(planId, "Review plan changed, please retry");
        Long criteriaVersionId = reviewRepository.insertCriteriaVersion(
                planId,
                1,
                request.getPlanName().trim() + " v1",
                totalWeight,
                operator.userId(),
                operator.userUuid()
        );
        requireInserted(criteriaVersionId, "Review criteria changed, please retry");
        for (int index = 0; index < criteria.size(); index += 1) {
            ReviewDTO.CriterionRequest criterion = criteria.get(index);
            int sortOrder = criterion.getSortOrder() == null ? (index + 1) * 10 : criterion.getSortOrder();
            requireInserted(
                    reviewRepository.insertCriterion(
                            criteriaVersionId,
                            criterion,
                            sortOrder,
                            operator.userId(),
                            operator.userUuid()
                    ),
                    "Review criterion changed, please retry"
            );
        }
        ReviewVO.Plan plan = requirePlan(planId);
        plan.setCriteria(reviewRepository.listCriteria(criteriaVersionId));
        return plan;
    }

    public ReviewVO.Plan getPlan(CurrentUser currentUser, Long planId) {
        requirePermission(currentUser, PLAN_MANAGE);
        ReviewVO.Plan plan = requirePlan(planId);
        Long criteriaVersionId = plan.getCriteriaVersionId() == null
                ? reviewRepository.findDraftCriteriaVersionId(planId)
                : plan.getCriteriaVersionId();
        plan.setCriteria(criteriaVersionId == null ? List.of() : reviewRepository.listCriteria(criteriaVersionId));
        return plan;
    }

    public List<ReviewVO.Plan> listPlans(CurrentUser currentUser, Long competitionId, Long stageId) {
        requirePermission(currentUser, PLAN_MANAGE);
        requireOptionalPositiveId(competitionId, "Competition id must be positive");
        requireOptionalPositiveId(stageId, "Review stage id must be positive");
        List<ReviewVO.Plan> plans = reviewRepository.listPlans(competitionId, stageId);
        for (ReviewVO.Plan plan : plans) {
            Long criteriaVersionId = plan.getCriteriaVersionId() == null
                    ? reviewRepository.findDraftCriteriaVersionId(plan.getId())
                    : plan.getCriteriaVersionId();
            plan.setCriteria(criteriaVersionId == null ? List.of() : reviewRepository.listCriteria(criteriaVersionId));
        }
        return plans;
    }

    @Transactional
    public ReviewVO.Plan activatePlan(CurrentUser currentUser, Long planId) {
        Operator operator = requirePermission(currentUser, PLAN_MANAGE);
        ReviewVO.Plan plan = requirePlan(planId);
        if (!"DRAFT".equals(plan.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only draft review plans can be activated");
        }
        Long criteriaVersionId = reviewRepository.findDraftCriteriaVersionId(planId);
        if (criteriaVersionId == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Review criteria version is missing");
        }
        List<ReviewVO.Criterion> criteria = reviewRepository.listCriteria(criteriaVersionId);
        if (criteria.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "Review criteria cannot be empty");
        }
        LocalDateTime now = LocalDateTime.now();
        requireWrite(
                reviewRepository.publishCriteriaVersion(
                        criteriaVersionId,
                        sha256(criteria),
                        operator.userId(),
                        operator.userUuid(),
                        now
                ),
                "Review criteria changed, please retry"
        );
        requireWrite(
                reviewRepository.markPlanReady(
                        planId,
                        criteriaVersionId,
                        plan.getVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        now
                ),
                "Review plan changed, please retry"
        );
        return getPlan(currentUser, planId);
    }

    @Transactional
    public ReviewVO.Batch createBatch(CurrentUser currentUser, ReviewDTO.BatchCreateRequest request) {
        Operator operator = requirePermission(currentUser, BATCH_CREATE);
        if (request == null || request.getPlanId() == null || request.getPlanId() <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review plan is required");
        }
        if (!StringUtils.hasText(request.getBatchName())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review batch name is required");
        }
        ReviewVO.Plan plan = requirePlan(request.getPlanId());
        if (!"READY".equals(plan.getStatus()) || plan.getCriteriaVersionId() == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Review plan must be ready before creating a batch");
        }
        String assignmentStrategy = normalizeEnum(
                request.getAssignmentStrategy(),
                "MANUAL",
                ASSIGNMENT_STRATEGIES,
                "Invalid assignment strategy"
        );
        int reviewerCountPerCandidate = defaultInt(
                request.getReviewerCountPerCandidate(),
                DEFAULT_REVIEWER_COUNT_PER_CANDIDATE
        );
        int expertMinAssignments = defaultInt(request.getExpertMinAssignments(), DEFAULT_EXPERT_MIN_ASSIGNMENTS);
        int expertTargetAssignments = defaultInt(
                request.getExpertTargetAssignments(),
                DEFAULT_EXPERT_TARGET_ASSIGNMENTS
        );
        int expertMaxAssignments = defaultInt(request.getExpertMaxAssignments(), DEFAULT_EXPERT_MAX_ASSIGNMENTS);
        if (reviewerCountPerCandidate <= 0
                || expertMinAssignments < 0
                || expertTargetAssignments < 0
                || expertMaxAssignments < 0
                || expertMinAssignments > expertTargetAssignments
                || expertTargetAssignments > expertMaxAssignments) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Expert workload must satisfy min <= target <= max");
        }
        if (expertMaxAssignments == 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Expert maximum workload must be positive");
        }
        request.setReviewerCountPerCandidate(reviewerCountPerCandidate);
        request.setExpertMinAssignments(expertMinAssignments);
        request.setExpertTargetAssignments(expertTargetAssignments);
        request.setExpertMaxAssignments(expertMaxAssignments);
        String batchNo = "RB-" + LocalDateTime.now().format(BATCH_TIME) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Long batchId = reviewRepository.insertBatch(
                plan,
                batchNo,
                request,
                assignmentStrategy,
                operator.userId(),
                operator.userUuid()
        );
        requireInserted(batchId, "Review batch changed, please retry");
        return reviewRepository.findBatch(batchId)
                .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Review batch could not be reloaded"));
    }

    public ReviewVO.Batch getBatch(CurrentUser currentUser, Long batchId) {
        requirePermission(currentUser, BATCH_CREATE);
        return requireBatch(batchId);
    }

    public List<ReviewVO.Batch> listBatches(CurrentUser currentUser, Long planId, Long competitionId) {
        requirePermission(currentUser, BATCH_CREATE);
        requireOptionalPositiveId(planId, "Review plan id must be positive");
        requireOptionalPositiveId(competitionId, "Competition id must be positive");
        return reviewRepository.listBatches(planId, competitionId);
    }

    public List<ReviewVO.Candidate> listCandidates(CurrentUser currentUser, Long batchId) {
        requirePermission(currentUser, ASSIGNMENT_MANAGE);
        requireBatch(batchId);
        return reviewRepository.listCandidates(batchId);
    }

    public List<ReviewVO.AdminAssignment> listAssignments(CurrentUser currentUser, Long batchId) {
        requirePermission(currentUser, ASSIGNMENT_MANAGE);
        requireBatch(batchId);
        return reviewRepository.listAssignments(batchId);
    }

    public List<ReviewVO.RosterExpert> listRoster(CurrentUser currentUser, Long batchId) {
        requirePermission(currentUser, ROSTER_MANAGE);
        requireBatch(batchId);
        return reviewRepository.listRoster(batchId);
    }

    @Transactional
    public List<ReviewVO.RosterExpert> saveRoster(
            CurrentUser currentUser,
            Long batchId,
            ReviewDTO.RosterSaveRequest request
    ) {
        Operator operator = requirePermission(currentUser, ROSTER_MANAGE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!Set.of("READY", "ASSIGNING").contains(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "The reviewer roster can only be changed before review starts");
        }
        List<Long> expertIds = normalizeExpertIds(request == null ? null : request.getExpertIds());
        List<ReviewRepository.ExpertRosterCandidate> experts = listEligibleExperts(expertIds);
        if (experts.size() != expertIds.size()) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    "Only approved, active experts with enabled accounts and email can be invited"
            );
        }
        Set<Long> selectedExpertIds = new HashSet<>(expertIds);
        List<ReviewVO.AdminAssignment> currentAssignments = reviewRepository.listAssignments(batchId);
        for (ReviewVO.AdminAssignment assignment : currentAssignments == null ? List.<ReviewVO.AdminAssignment>of() : currentAssignments) {
            if (!Set.of("DECLINED", "EXPIRED", "REVOKED").contains(assignment.getStatus())
                    && !selectedExpertIds.contains(assignment.getExpertId())) {
                throw biz(
                        ErrorCode.BIZ_ERROR,
                        "Assigned experts must be revoked with a reason before leaving the roster"
                );
            }
        }
        requireWrite(
                reviewRepository.replaceRoster(
                        batchId,
                        experts,
                        operator.userId(),
                        operator.userUuid(),
                        LocalDateTime.now()
                ),
                "Reviewer roster changed, please retry"
        );
        return reviewRepository.listRoster(batchId);
    }

    @Transactional
    public ReviewVO.Batch confirmAssignments(CurrentUser currentUser, Long batchId) {
        Operator operator = requirePermission(currentUser, ASSIGNMENT_MANAGE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!"ASSIGNING".equals(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only assigning review batches can confirm assignments");
        }
        validateAssignmentAllocation(batchId, batch);
        requireWrite(
                reviewRepository.markBatchAssignmentsConfirmed(
                        batchId,
                        batch.getVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        LocalDateTime.now()
                ),
                "Review assignment changed, please retry"
        );
        return requireBatch(batchId);
    }

    public List<ReviewVO.RosterExpert> listInvitations(CurrentUser currentUser, Long batchId) {
        requirePermission(currentUser, NOTIFICATION_SEND);
        requireBatch(batchId);
        return reviewRepository.listRoster(batchId);
    }

    @Transactional
    public List<ReviewVO.RosterExpert> sendInvitations(CurrentUser currentUser, Long batchId) {
        Operator operator = requirePermission(currentUser, NOTIFICATION_SEND);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!Set.of("ASSIGNING", "IN_REVIEW").contains(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Invitations can only be sent after candidates and assignments are ready");
        }
        List<Long> rosterExpertIds = selectedRosterExpertIds(batchId);
        if (rosterExpertIds.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "Select the complete reviewer roster before sending invitations");
        }
        if (batch.getAssignmentConfirmedAt() == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Confirm the project-to-expert allocation before sending invitations");
        }
        validateAssignmentAllocation(batchId, batch);
        List<ReviewVO.RosterExpert> roster = reviewRepository.listRoster(batchId);
        ReviewNotificationPort notificationPort = reviewNotificationPortProvider == null
                ? null : reviewNotificationPortProvider.getIfAvailable();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(INVITATION_TTL_HOURS);
        int sentCount = 0;
        for (ReviewVO.RosterExpert reviewer : roster) {
            ReviewRepository.ExpertRosterCandidate expert = new ReviewRepository.ExpertRosterCandidate(
                    reviewer.getExpertId(),
                    reviewer.getExpertUserId(),
                    reviewer.getExpertUserUuid(),
                    reviewer.getExpertName(),
                    reviewer.getEmail(),
                    "active",
                    "APPROVED",
                    "ENABLED"
            );
            String rawToken = randomToken();
            Long invitationId = reviewRepository.upsertInvitation(
                    batchId,
                    reviewer.getId(),
                    expert,
                    sha256Text(rawToken),
                    expiresAt,
                    operator.userId(),
                    operator.userUuid(),
                    now
            );
            requireInserted(invitationId, "Review invitation changed, please retry");
            int attempts = (reviewer.getInvitationAttempts() == null ? 0 : reviewer.getInvitationAttempts()) + 1;
            String invitationUrl = reviewInvitationUrl + "?token=" + rawToken;
            String subject = "评审邀请：" + batch.getBatchName();
            String content = buildInvitationContent(batch, reviewer, invitationUrl, expiresAt);
            Long outboxId = reviewRepository.enqueueInvitationNotification(
                    batchId,
                    invitationId,
                    sha256Text(rawToken),
                    reviewer.getEmail(),
                    subject,
                    content,
                    operator.userId(),
                    operator.userUuid(),
                    now
            );
            requireInserted(outboxId, "Review invitation notification changed, please retry");
            try {
                if (notificationPort == null) {
                    throw new IllegalStateException("Review invitation mail service is unavailable");
                }
                notificationPort.sendReviewInvitation(
                        reviewer.getEmail(),
                        subject,
                        content
                );
                requireWrite(
                        reviewRepository.markInvitationNotificationSent(outboxId, attempts, now),
                        "Review invitation notification changed, please retry"
                );
                requireWrite(
                        reviewRepository.markInvitationSent(invitationId, attempts, now),
                        "Review invitation delivery state changed, please retry"
                );
                sentCount++;
            } catch (RuntimeException exception) {
                String reason = exception.getMessage();
                reviewRepository.markInvitationFailed(
                        invitationId,
                        attempts,
                        StringUtils.hasText(reason) ? reason.substring(0, Math.min(reason.length(), 1000)) : "Email delivery failed",
                        now
                );
                reviewRepository.markInvitationNotificationFailed(
                        outboxId,
                        attempts,
                        StringUtils.hasText(reason) ? reason.substring(0, Math.min(reason.length(), 1000)) : "Email delivery failed",
                        now
                );
            }
        }
        recordInvitationDispatchEvent(operator, batch, sentCount, roster.size());
        return reviewRepository.listRoster(batchId);
    }

    @Transactional
    public ReviewVO.Invitation openInvitation(String rawToken) {
        ReviewRepository.InvitationContext context = requireInvitationContext(rawToken, false);
        if (context.checkedInAt() != null) {
            return toInvitation(context, null);
        }
        LocalDateTime now = LocalDateTime.now();
        String qrValue = randomToken();
        LocalDateTime qrExpiresAt = now.plusMinutes(CHECKIN_QR_TTL_MINUTES);
        requireWrite(
                reviewRepository.issueInvitationQr(
                        context.invitationId(),
                        sha256Text(qrValue),
                        qrExpiresAt,
                        now
                ),
                "The invitation has expired or is no longer valid"
        );
        return toInvitation(
                new ReviewRepository.InvitationContext(
                        context.invitationId(), context.batchId(), context.batchName(), context.rosterId(),
                        context.expertId(), context.expertUserId(), context.expertUserUuid(), context.expertName(),
                        context.email(), "QR_ISSUED", context.tokenHash(), context.tokenExpiresAt(),
                        qrExpiresAt, context.checkedInAt(), context.sentAt(), context.failureReason()
                ),
                qrValue
        );
    }

    public ReviewVO.Invitation invitationStatus(String rawToken) {
        return toInvitation(requireInvitationContext(rawToken, false), null);
    }

    @Transactional
    public ReviewVO.Invitation checkIn(
            CurrentUser currentUser,
            Long batchId,
            ReviewDTO.CheckInRequest request
    ) {
        Operator operator = requirePermission(currentUser, CHECKIN_SCAN);
        if (batchId == null || batchId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review batch id is required");
        }
        if (request == null || !StringUtils.hasText(request.getQrToken())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "The check-in QR token is required");
        }
        String qrTokenHash = sha256Text(request.getQrToken().trim());
        Optional<ReviewRepository.InvitationContext> optional =
                reviewRepository.findInvitationByQrTokenHash(qrTokenHash);
        if (optional.isEmpty()) {
            reviewRepository.recordCheckinAttempt(
                    batchId, null, null, qrTokenHash, "REJECTED", "二维码无效或已被替换",
                    operator.userId(), operator.userUuid(), LocalDateTime.now()
            );
            throw biz(ErrorCode.BIZ_ERROR, "The check-in QR token is invalid or has been replaced");
        }
        ReviewRepository.InvitationContext context = optional.get();
        if (!invitationMatchesEligibleExpert(context)) {
            reviewRepository.recordCheckinAttempt(
                    batchId, null, null, qrTokenHash, "REJECTED", "二维码无效或已被替换",
                    operator.userId(), operator.userUuid(), LocalDateTime.now()
            );
            throw biz(ErrorCode.BIZ_ERROR, "The check-in QR token is invalid or has been replaced");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!batchId.equals(context.batchId())) {
            recordRejectedCheckin(operator, batchId, context, qrTokenHash, "二维码不属于当前评审批次");
            throw biz(ErrorCode.BIZ_ERROR, "The QR token belongs to another review batch");
        }
        if (context.checkedInAt() != null || "CHECKED_IN".equals(context.invitationStatus())) {
            recordRejectedCheckin(operator, batchId, context, qrTokenHash, "专家已签到，二维码不可重复使用");
            throw biz(ErrorCode.BIZ_ERROR, "This expert has already checked in");
        }
        if (context.qrExpiresAt() == null || !context.qrExpiresAt().isAfter(now)) {
            recordRejectedCheckin(operator, batchId, context, qrTokenHash, "签到二维码已过期");
            throw biz(ErrorCode.BIZ_ERROR, "The check-in QR token has expired");
        }
        if (!"QR_ISSUED".equals(context.invitationStatus())) {
            recordRejectedCheckin(operator, batchId, context, qrTokenHash, "二维码当前不可签到");
            throw biz(ErrorCode.BIZ_ERROR, "The check-in QR token is not ready");
        }
        requireWrite(
                reviewRepository.markInvitationCheckedIn(
                        context.invitationId(), operator.userId(), operator.userUuid(), now
                ),
                "The check-in QR token was already used or expired"
        );
        reviewRepository.recordCheckinAttempt(
                batchId, context.invitationId(), context.expertId(), qrTokenHash, "SUCCESS", null,
                operator.userId(), operator.userUuid(), now
        );
        return toInvitation(
                new ReviewRepository.InvitationContext(
                        context.invitationId(), context.batchId(), context.batchName(), context.rosterId(),
                        context.expertId(), context.expertUserId(), context.expertUserUuid(), context.expertName(),
                        context.email(), "CHECKED_IN", context.tokenHash(), context.tokenExpiresAt(),
                        context.qrExpiresAt(), now, context.sentAt(), context.failureReason()
                ),
                null
        );
    }

    public List<ReviewVO.AssignmentTask> listInvitationAssignments(String rawToken) {
        ReviewRepository.InvitationContext context = requireInvitationContext(rawToken, true);
        return reviewRepository.listOwnedAssignments(context.expertUserId(), context.expertUserUuid()).stream()
                .filter(task -> context.batchId().equals(task.getBatchId()))
                .toList();
    }

    @Transactional
    public ReviewVO.AssignmentTask acceptInvitationAssignment(String rawToken, Long assignmentId) {
        ReviewRepository.InvitationContext context = requireInvitationContext(rawToken, true);
        return acceptAssignmentAsOperator(
                new Operator(context.expertUserId(), context.expertUserUuid()),
                assignmentId,
                context.batchId()
        );
    }

    @Transactional
    public ReviewVO.AssignmentTask declineInvitationAssignment(
            String rawToken,
            Long assignmentId,
            ReviewDTO.AssignmentDeclineRequest request
    ) {
        ReviewRepository.InvitationContext context = requireInvitationContext(rawToken, true);
        return declineAssignmentAsOperator(
                new Operator(context.expertUserId(), context.expertUserUuid()),
                assignmentId,
                request,
                context.batchId()
        );
    }

    @Transactional
    public ReviewVO.ReviewSheet saveInvitationDraft(
            String rawToken,
            Long assignmentId,
            ReviewDTO.ReviewSheetRequest request
    ) {
        ReviewRepository.InvitationContext context = requireInvitationContext(rawToken, true);
        return writeSheet(
                new Operator(context.expertUserId(), context.expertUserUuid()),
                assignmentId,
                request,
                false,
                context.batchId()
        );
    }

    @Transactional
    public ReviewVO.ReviewSheet submitInvitationSheet(
            String rawToken,
            Long assignmentId,
            ReviewDTO.ReviewSheetRequest request
    ) {
        ReviewRepository.InvitationContext context = requireInvitationContext(rawToken, true);
        return writeSheet(
                new Operator(context.expertUserId(), context.expertUserUuid()),
                assignmentId,
                request,
                true,
                context.batchId()
        );
    }

    public List<ReviewVO.Aggregate> listAggregates(CurrentUser currentUser, Long batchId) {
        requirePermission(currentUser, RESULT_AGGREGATE);
        requireBatch(batchId);
        return reviewRepository.listAggregates(batchId);
    }

    public ReviewVO.Publication getLatestPublication(CurrentUser currentUser, Long batchId) {
        requirePermission(currentUser, RESULT_PUBLISH);
        requireBatch(batchId);
        return reviewRepository.findLatestPublication(batchId)
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review publication not found"));
    }

    public List<ReviewVO.PublishedResult> listMyPublishedResults(CurrentUser currentUser) {
        Operator operator = requirePermission(currentUser, APPEAL_SUBMIT);
        TeamInternalApi teamInternalApi = teamInternalApi();
        if (teamInternalApi == null) {
            return reviewRepository.listOwnedPublishedResults(operator.userId(), operator.userUuid());
        }
        return reviewRepository.listOwnedPublishedResults(
                operator.userId(), operator.userUuid(), activeTeamIdsForUser(operator, teamInternalApi)
        );
    }

    public List<ReviewVO.Appeal> listMyAppeals(CurrentUser currentUser) {
        Operator operator = requirePermission(currentUser, APPEAL_SUBMIT);
        return reviewRepository.listOwnedAppeals(operator.userId(), operator.userUuid());
    }

    public List<ReviewVO.Appeal> listAppeals(
            CurrentUser currentUser,
            Long batchId,
            String status
    ) {
        requirePermission(currentUser, APPEAL_MANAGE);
        requireOptionalPositiveId(batchId, "Review batch id must be positive");
        String normalizedStatus = StringUtils.hasText(status)
                ? normalizeEnum(
                        status,
                        null,
                        Set.of("SUBMITTED", "ACCEPTED", "REJECTED"),
                        "Invalid appeal status"
                )
                : null;
        return reviewRepository.listAppeals(batchId, normalizedStatus);
    }

    @Transactional
    public ReviewVO.Appeal submitAppeal(
            CurrentUser currentUser,
            Long publicationId,
            Long registrationId,
            ReviewDTO.AppealSubmitRequest request
    ) {
        Operator operator = requirePermission(currentUser, APPEAL_SUBMIT);
        if (publicationId == null || publicationId <= 0
                || registrationId == null || registrationId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Publication and registration are required");
        }
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Appeal reason is required");
        }
        TeamInternalApi teamInternalApi = teamInternalApi();
        ReviewRepository.AppealTarget target = (teamInternalApi == null
                ? reviewRepository.findOwnedAppealTarget(
                        publicationId, registrationId, operator.userId(), operator.userUuid()
                )
                : reviewRepository.findOwnedAppealTarget(
                        publicationId, registrationId, operator.userId(), operator.userUuid(),
                        activeTeamIdsForUser(operator, teamInternalApi)
                ))
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Published review result not found"));
        ReviewVO.Appeal existing = reviewRepository.findAppealByPublicationAndRegistration(
                publicationId,
                registrationId
        ).orElse(null);
        if (existing != null) {
            return existing;
        }
        String appealNo = "RA-" + LocalDateTime.now().format(BATCH_TIME) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Long appealId = reviewRepository.insertAppeal(
                target,
                appealNo,
                request.getReason().trim(),
                operator.userId(),
                operator.userUuid()
        );
        requireInserted(appealId, "Review appeal changed, please retry");
        ReviewVO.Appeal appeal = reviewRepository.findAppeal(appealId)
                .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Review appeal could not be reloaded"));
        publishAppealEvent(operator, appeal, "COMPETITION_REVIEW_APPEAL_SUBMITTED");
        increment(appealSubmittedCounter);
        return appeal;
    }

    @Transactional
    public ReviewVO.Appeal resolveAppeal(
            CurrentUser currentUser,
            Long appealId,
            ReviewDTO.AppealResolveRequest request
    ) {
        Operator operator = requirePermission(currentUser, APPEAL_MANAGE);
        if (appealId == null || appealId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review appeal id is required");
        }
        if (request == null || !StringUtils.hasText(request.getResolution())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Appeal resolution is required");
        }
        String decision = normalizeEnum(
                request.getDecision(),
                null,
                APPEAL_DECISIONS,
                "Invalid appeal decision"
        );
        ReviewVO.Appeal existing = reviewRepository.findAppeal(appealId)
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review appeal not found"));
        if (!"SUBMITTED".equals(existing.getStatus())) {
            return existing;
        }
        requireWrite(
                reviewRepository.resolveAppeal(
                        appealId,
                        decision,
                        request.getResolution().trim(),
                        operator.userId(),
                        operator.userUuid(),
                        LocalDateTime.now()
                ),
                "Review appeal changed, please retry"
        );
        ReviewVO.Appeal resolved = reviewRepository.findAppeal(appealId)
                .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Review appeal could not be reloaded"));
        publishAppealEvent(operator, resolved, "COMPETITION_REVIEW_APPEAL_RESOLVED");
        return resolved;
    }

    @Transactional
    public ReviewVO.Batch freezeBatch(
            CurrentUser currentUser,
            Long batchId,
            ReviewDTO.BatchFreezeRequest request
    ) {
        Operator operator = requirePermission(currentUser, BATCH_CREATE);
        if (batchId == null || batchId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review batch id is required");
        }
        ReviewVO.Batch batch = reviewRepository.findBatch(batchId)
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review batch not found"));
        if (!"DRAFT".equals(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only draft review batches can freeze candidates");
        }
        ReviewVO.Plan plan = requirePlan(batch.getPlanId());
        if (!"READY".equals(plan.getStatus())
                || plan.getCriteriaVersionId() == null
                || !plan.getCriteriaVersionId().equals(batch.getCriteriaVersionId())) {
            throw biz(ErrorCode.BIZ_ERROR, "Review batch criteria do not match the active plan");
        }
        List<Long> requestedRegistrationIds = normalizeRegistrationIds(
                request == null ? null : request.getRegistrationIds()
        );
        List<ReviewRepository.CandidateSnapshot> snapshots =
                loadCandidateSnapshots(batch.getCompetitionId(), requestedRegistrationIds);
        if (snapshots.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "No eligible paid or confirmed registrations were found");
        }
        if (!requestedRegistrationIds.isEmpty() && snapshots.size() != requestedRegistrationIds.size()) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    "Some registrations are missing, ineligible, or outside the competition dataset"
            );
        }

        boolean blind = !"NONE".equals(plan.getBlindMode());
        for (int index = 0; index < snapshots.size(); index += 1) {
            ReviewRepository.CandidateSnapshot snapshot = snapshots.get(index);
            String snapshotJson = immutableCandidateSnapshot(snapshot);
            String blindCode = blind ? String.format(Locale.ROOT, "C%05d", index + 1) : null;
            String reviewSnapshotJson = immutableReviewSnapshot(snapshot, plan.getBlindMode(), blindCode);
            requireInserted(
                    reviewRepository.insertCandidate(
                            batchId,
                            snapshot.registrationId(),
                            blindCode,
                            snapshotJson,
                            reviewSnapshotJson,
                            sha256Text(snapshotJson),
                            operator.userId(),
                            operator.userUuid()
                    ),
                    "Review candidate changed, please retry"
            );
        }
        String freezeToken = UUID.randomUUID().toString();
        LocalDateTime frozenAt = LocalDateTime.now();
        requireWrite(
                reviewRepository.markBatchReady(
                        batchId,
                        freezeToken,
                        snapshots.size(),
                        batch.getVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        frozenAt
                ),
                "Review batch changed, please retry"
        );
        return reviewRepository.findBatch(batchId)
                .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Review batch could not be reloaded"));
    }

    @Transactional
    public ReviewVO.AssignmentResult assignExperts(
            CurrentUser currentUser,
            Long batchId,
            ReviewDTO.AssignmentCreateRequest request
    ) {
        Operator operator = requirePermission(currentUser, ASSIGNMENT_MANAGE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!Set.of("READY", "ASSIGNING", "IN_REVIEW").contains(batch.getStatus())) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    "Experts can only be assigned to ready, assigning, or in-review batches"
            );
        }
        if (request == null || request.getAssignments() == null || request.getAssignments().isEmpty()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "At least one review assignment is required");
        }
        if (request.getAssignments().size() > 20000) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many review assignments");
        }
        LocalDateTime effectiveDueAt = request.getDueAt() == null
                ? batch.getReviewDeadline()
                : request.getDueAt();
        if (effectiveDueAt != null && !effectiveDueAt.isAfter(LocalDateTime.now())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review assignment deadline must be in the future");
        }

        Set<String> uniquePairs = new HashSet<>();
        Set<Long> rosterExpertSet = new HashSet<>(selectedRosterExpertIds(batchId));
        int createdCount = 0;
        for (ReviewDTO.AssignmentItemRequest item : request.getAssignments()) {
            if (item == null || item.getCandidateId() == null || item.getCandidateId() <= 0
                    || item.getExpertId() == null || item.getExpertId() <= 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Candidate and expert ids must be positive");
            }
            String pairKey = item.getCandidateId() + ":" + item.getExpertId();
            if (!uniquePairs.add(pairKey)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Review assignments must be unique");
            }
            ReviewRepository.AssignmentTarget target = reviewRepository.findAssignmentTarget(
                    batchId,
                    item.getCandidateId(),
                    item.getExpertId()
            ).map(this::attachExpertSnapshot)
                    .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review candidate or expert not found"));
            requireEligibleAssignmentTarget(target);
            if (!rosterExpertSet.isEmpty() && !rosterExpertSet.contains(target.expertId())) {
                throw biz(ErrorCode.BIZ_ERROR, "The expert must be selected in this review batch's roster first");
            }
            if (reviewRepository.assignmentExists(target.candidateId(), target.expertId())) {
                throw biz(ErrorCode.BIZ_ERROR, "The expert is already assigned to this candidate");
            }
            requireInserted(
                    reviewRepository.insertAssignment(
                            batchId,
                            target,
                            item.getReviewerWeight() == null ? BigDecimal.ONE : item.getReviewerWeight(),
                            effectiveDueAt,
                            operator.userId(),
                            operator.userUuid()
                    ),
                    "Review assignment changed, please retry"
            );
            createdCount++;
        }

        if ("READY".equals(batch.getStatus())) {
            ReviewBatchStatus.READY.requireTransitionTo(ReviewBatchStatus.ASSIGNING);
            requireWrite(
                    reviewRepository.markBatchAssigning(
                            batchId,
                            batch.getVersion(),
                            operator.userId(),
                            operator.userUuid(),
                            LocalDateTime.now()
                    ),
                    "Review batch changed, please retry"
            );
            batch = requireBatch(batchId);
        }
        int belowMinimum = reviewRepository.countCandidatesBelowMinimumAssignments(
                batchId,
                reviewerCount(batch)
        );
        ReviewVO.AssignmentResult result = new ReviewVO.AssignmentResult();
        result.setBatchId(batchId);
        result.setBatchStatus(batch.getStatus());
        result.setCreatedCount(createdCount);
        result.setCandidateCount(batch.getCandidateCount());
        result.setCandidatesBelowMinimum(belowMinimum);
        return result;
    }

    @Transactional
    public ReviewVO.AssignmentResult autoAssignExperts(
            CurrentUser currentUser,
            Long batchId,
            ReviewDTO.AutoAssignmentRequest request
    ) {
        requirePermission(currentUser, ASSIGNMENT_MANAGE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!Set.of("READY", "ASSIGNING", "IN_REVIEW").contains(batch.getStatus())) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    "Experts can only be assigned to ready, assigning, or in-review batches"
            );
        }
        Set<Long> requestedExpertIds = new HashSet<>();
        if (request != null && request.getExpertIds() != null) {
            for (Long expertId : request.getExpertIds()) {
                if (expertId == null || expertId <= 0 || !requestedExpertIds.add(expertId)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Expert ids must be positive and unique");
                }
            }
        }
        List<ReviewVO.Candidate> candidates = reviewRepository.listCandidates(batchId);
        if (candidates.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "The review batch has no frozen candidates");
        }
        List<ReviewVO.AdminAssignment> existingAssignments = reviewRepository.listAssignments(batchId);
        if (existingAssignments == null) {
            existingAssignments = List.of();
        }
        List<Long> rosterExpertIds = selectedRosterExpertIds(batchId);
        Set<Long> rosterSet = new HashSet<>(rosterExpertIds);
        if (!rosterSet.isEmpty() && !rosterSet.containsAll(requestedExpertIds)) {
            throw biz(ErrorCode.BIZ_ERROR, "Every automatically selected expert must be in the review roster");
        }
        List<ReviewRepository.ExpertWorkload> availableExperts;
        if (!rosterExpertIds.isEmpty()) {
            List<ReviewRepository.ExpertRosterCandidate> rosterCandidates = listEligibleExperts(rosterExpertIds);
            if (rosterCandidates == null || rosterCandidates.size() != rosterExpertIds.size()) {
                throw biz(ErrorCode.BIZ_ERROR, "Some roster experts are no longer approved or enabled");
            }
            Map<Long, Integer> currentBatchWorkloads = new HashMap<>();
            for (ReviewVO.AdminAssignment assignment : existingAssignments) {
                if (!Set.of("DECLINED", "EXPIRED", "REVOKED").contains(assignment.getStatus())) {
                    currentBatchWorkloads.merge(assignment.getExpertId(), 1, Integer::sum);
                }
            }
            availableExperts = rosterCandidates.stream()
                    .filter(item -> requestedExpertIds.isEmpty() || requestedExpertIds.contains(item.expertId()))
                    .map(item -> new ReviewRepository.ExpertWorkload(
                            item.expertId(), currentBatchWorkloads.getOrDefault(item.expertId(), 0)
                    ))
                    .toList();
        } else {
            availableExperts = availableExpertWorkloads().stream()
                    .filter(item -> requestedExpertIds.isEmpty() || requestedExpertIds.contains(item.expertId()))
                    .toList();
        }
        if (!requestedExpertIds.isEmpty() && availableExperts.size() != requestedExpertIds.size()) {
            throw biz(ErrorCode.BIZ_ERROR, "Some selected experts are unavailable or not approved");
        }
        if (availableExperts.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "No approved experts are available for assignment");
        }

        Set<String> existingPairs = new HashSet<>();
        Map<Long, Integer> candidateCounts = new HashMap<>();
        for (ReviewVO.AdminAssignment assignment : existingAssignments) {
            existingPairs.add(assignment.getCandidateId() + ":" + assignment.getExpertId());
            if (!Set.of("DECLINED", "EXPIRED", "REVOKED").contains(assignment.getStatus())) {
                candidateCounts.merge(assignment.getCandidateId(), 1, Integer::sum);
            }
        }
        Map<Long, Integer> workloads = new HashMap<>();
        for (ReviewRepository.ExpertWorkload expert : availableExperts) {
            workloads.put(expert.expertId(), expert.activeAssignmentCount());
        }
        int maxAssignments = expertMaxAssignments(batch);
        int targetAssignments = expertTargetAssignments(batch);
        if (maxAssignments <= 0 || expertMinAssignments(batch) > targetAssignments || targetAssignments > maxAssignments) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Expert workload must satisfy min <= target <= max");
        }
        for (Map.Entry<Long, Integer> entry : workloads.entrySet()) {
            if (entry.getValue() > maxAssignments) {
                throw biz(ErrorCode.BIZ_ERROR, "An expert already exceeds this batch's maximum workload");
            }
        }

        List<ReviewDTO.AssignmentItemRequest> plannedAssignments = new ArrayList<>();
        for (ReviewVO.Candidate candidate : candidates) {
            int missing = Math.max(
                    0,
                    reviewerCount(batch) - candidateCounts.getOrDefault(candidate.getId(), 0)
            );
            for (int slot = 0; slot < missing; slot += 1) {
                Long selectedExpertId = null;
                List<ReviewRepository.ExpertWorkload> rankedExperts = availableExperts.stream()
                        .sorted(
                                Comparator.comparingInt(
                                                (ReviewRepository.ExpertWorkload item) ->
                                                        workloads.getOrDefault(item.expertId(), 0) < targetAssignments ? 0 : 1
                                        )
                                        .thenComparingInt(item -> workloads.getOrDefault(item.expertId(), 0))
                                        .thenComparing(ReviewRepository.ExpertWorkload::expertId)
                        )
                        .toList();
                for (ReviewRepository.ExpertWorkload expert : rankedExperts) {
                    if (workloads.getOrDefault(expert.expertId(), 0) >= maxAssignments) {
                        continue;
                    }
                    String pairKey = candidate.getId() + ":" + expert.expertId();
                    if (existingPairs.contains(pairKey)) {
                        continue;
                    }
                    ReviewRepository.AssignmentTarget target = reviewRepository.findAssignmentTarget(
                            batchId,
                            candidate.getId(),
                            expert.expertId()
                    ).map(this::attachExpertSnapshot).orElse(null);
                    if (target != null && isEligibleAssignmentTarget(target)) {
                        selectedExpertId = expert.expertId();
                        break;
                    }
                }
                if (selectedExpertId == null) {
                    throw biz(
                            ErrorCode.BIZ_ERROR,
                            "Not enough conflict-free approved experts to cover every candidate"
                    );
                }
                ReviewDTO.AssignmentItemRequest item = new ReviewDTO.AssignmentItemRequest();
                item.setCandidateId(candidate.getId());
                item.setExpertId(selectedExpertId);
                item.setReviewerWeight(
                        request == null || request.getReviewerWeight() == null
                                ? BigDecimal.ONE
                                : request.getReviewerWeight()
                );
                plannedAssignments.add(item);
                existingPairs.add(candidate.getId() + ":" + selectedExpertId);
                workloads.merge(selectedExpertId, 1, Integer::sum);
            }
        }

        if (plannedAssignments.isEmpty()) {
            ReviewVO.AssignmentResult result = new ReviewVO.AssignmentResult();
            result.setBatchId(batchId);
            result.setBatchStatus(batch.getStatus());
            result.setCreatedCount(0);
            result.setCandidateCount(batch.getCandidateCount());
            result.setCandidatesBelowMinimum(0);
            return result;
        }
        ReviewDTO.AssignmentCreateRequest assignmentRequest = new ReviewDTO.AssignmentCreateRequest();
        assignmentRequest.setAssignments(plannedAssignments);
        assignmentRequest.setDueAt(request == null ? null : request.getDueAt());
        return assignExperts(currentUser, batchId, assignmentRequest);
    }

    @Transactional
    public ReviewVO.Batch startReview(CurrentUser currentUser, Long batchId) {
        Operator operator = requirePermission(currentUser, ASSIGNMENT_MANAGE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!"ASSIGNING".equals(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only assigning review batches can start review");
        }
        List<Long> rosterExpertIds = selectedRosterExpertIds(batchId);
        if (!rosterExpertIds.isEmpty()
                && batch.getAssignmentConfirmedAt() == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Confirm the reviewer roster and workload allocation before starting review");
        }
        if (!rosterExpertIds.isEmpty()) {
            validateAssignmentAllocation(batchId, batch);
        }
        int belowMinimum = reviewRepository.countCandidatesBelowMinimumAssignments(
                batchId,
                reviewerCount(batch)
        );
        if (belowMinimum > 0) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    belowMinimum + " candidates have fewer than the required number of reviewers"
            );
        }
        ReviewBatchStatus.ASSIGNING.requireTransitionTo(ReviewBatchStatus.IN_REVIEW);
        requireWrite(
                reviewRepository.markBatchInReview(
                        batchId,
                        batch.getVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        LocalDateTime.now()
                ),
                "Review batch changed, please retry"
        );
        return requireBatch(batchId);
    }

    public List<ReviewVO.AssignmentTask> listMyAssignments(CurrentUser currentUser) {
        Operator operator = requirePermission(currentUser, TASK_VIEW);
        return reviewRepository.listOwnedAssignments(operator.userId(), operator.userUuid());
    }

    @Transactional
    public ReviewVO.AssignmentTask acceptAssignment(CurrentUser currentUser, Long assignmentId) {
        Operator operator = requirePermission(currentUser, TASK_VIEW);
        return acceptAssignmentAsOperator(operator, assignmentId, null);
    }

    private ReviewVO.AssignmentTask acceptAssignmentAsOperator(
            Operator operator,
            Long assignmentId,
            Long expectedBatchId
    ) {
        ReviewRepository.AssignmentContext assignment = requireOwnedAssignment(assignmentId, operator, expectedBatchId);
        if (!Set.of("ASSIGNING", "IN_REVIEW").contains(assignment.batchStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "The review batch is not accepting assignment responses");
        }
        if (!"ASSIGNED".equals(assignment.assignmentStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only assigned review tasks can be accepted");
        }
        requireWrite(
                reviewRepository.acceptAssignment(
                        assignmentId,
                        assignment.assignmentVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        LocalDateTime.now()
                ),
                "Review assignment changed, please retry"
        );
        return requireOwnedTask(assignmentId, operator);
    }

    @Transactional
    public ReviewVO.AssignmentTask declineAssignment(
            CurrentUser currentUser,
            Long assignmentId,
            ReviewDTO.AssignmentDeclineRequest request
    ) {
        Operator operator = requirePermission(currentUser, TASK_VIEW);
        return declineAssignmentAsOperator(operator, assignmentId, request, null);
    }

    private ReviewVO.AssignmentTask declineAssignmentAsOperator(
            Operator operator,
            Long assignmentId,
            ReviewDTO.AssignmentDeclineRequest request,
            Long expectedBatchId
    ) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Decline reason is required");
        }
        ReviewRepository.AssignmentContext assignment = requireOwnedAssignment(assignmentId, operator, expectedBatchId);
        if (!Set.of("ASSIGNING", "IN_REVIEW").contains(assignment.batchStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "The review batch is not accepting assignment responses");
        }
        if (!"ASSIGNED".equals(assignment.assignmentStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only assigned review tasks can be declined");
        }
        requireWrite(
                reviewRepository.declineAssignment(
                        assignmentId,
                        assignment.assignmentVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        request.getReason().trim(),
                        LocalDateTime.now()
                ),
                "Review assignment changed, please retry"
        );
        return requireOwnedTask(assignmentId, operator);
    }

    @Transactional
    public ReviewVO.AdminAssignment revokeAssignment(
            CurrentUser currentUser,
            Long batchId,
            Long assignmentId,
            ReviewDTO.AssignmentRevokeRequest request
    ) {
        Operator operator = requirePermission(currentUser, ASSIGNMENT_MANAGE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!Set.of("ASSIGNING", "IN_REVIEW").contains(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Assignments can only be revoked before aggregation");
        }
        if (assignmentId == null || assignmentId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review assignment id is required");
        }
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Assignment revoke reason is required");
        }
        ReviewVO.AdminAssignment assignment = reviewRepository.listAssignments(batchId).stream()
                .filter(item -> assignmentId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review assignment not found"));
        if (Set.of("DECLINED", "EXPIRED", "REVOKED").contains(assignment.getStatus())) {
            return assignment;
        }
        if ("SUBMITTED".equals(assignment.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Submitted review assignments cannot be revoked");
        }
        requireWrite(
                reviewRepository.revokeAssignment(
                        batchId,
                        assignmentId,
                        request.getReason().trim(),
                        operator.userId(),
                        operator.userUuid(),
                        LocalDateTime.now()
                ),
                "Review assignment changed, please retry"
        );
        return reviewRepository.listAssignments(batchId).stream()
                .filter(item -> assignmentId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Review assignment could not be reloaded"));
    }

    @Transactional
    public int expireDueAssignments() {
        return reviewRepository.expireDueAssignments(LocalDateTime.now());
    }

    @Transactional
    public ReviewVO.ReviewSheet saveDraft(
            CurrentUser currentUser,
            Long assignmentId,
            ReviewDTO.ReviewSheetRequest request
    ) {
        Operator operator = requirePermission(currentUser, SCORE_SUBMIT);
        return writeSheet(operator, assignmentId, request, false, null);
    }

    @Transactional
    public ReviewVO.ReviewSheet submitSheet(
            CurrentUser currentUser,
            Long assignmentId,
            ReviewDTO.ReviewSheetRequest request
    ) {
        Operator operator = requirePermission(currentUser, SCORE_SUBMIT);
        return writeSheet(operator, assignmentId, request, true, null);
    }

    private ReviewVO.ReviewSheet writeSheet(
            Operator operator,
            Long assignmentId,
            ReviewDTO.ReviewSheetRequest request,
            boolean submit,
            Long expectedBatchId
    ) {
        ReviewRepository.AssignmentContext assignment = requireOwnedAssignment(assignmentId, operator, expectedBatchId);
        if (!"IN_REVIEW".equals(assignment.batchStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Scores can only be recorded while the batch is in review");
        }
        if (!Set.of("ACCEPTED", "IN_PROGRESS").contains(assignment.assignmentStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "The review assignment must be accepted and not yet submitted");
        }
        BigDecimal totalScore = validateAndCalculateScore(assignment, request);
        int versionNo = reviewRepository.findLatestSheetVersion(assignmentId) + 1;
        LocalDateTime now = LocalDateTime.now();
        String status = submit ? "SUBMITTED" : "DRAFT";
        Long sheetId = reviewRepository.insertSheet(
                assignment,
                versionNo,
                status,
                totalScore,
                request.getReviewComment(),
                submit ? now : null,
                operator.userId(),
                operator.userUuid()
        );
        requireInserted(sheetId, "Review sheet changed, please retry");
        for (ReviewDTO.ScoreItemRequest score : request.getScores()) {
            requireInserted(
                    reviewRepository.insertScoreItem(
                            sheetId,
                            score,
                            operator.userId(),
                            operator.userUuid()
                    ),
                    "Review score changed, please retry"
            );
        }
        if (submit) {
            requireWrite(
                    reviewRepository.markAssignmentSubmitted(
                            assignmentId,
                            assignment.assignmentVersion(),
                            operator.userId(),
                            operator.userUuid(),
                            now
                    ),
                    "Review assignment changed, please retry"
            );
        } else if ("ACCEPTED".equals(assignment.assignmentStatus())) {
            requireWrite(
                    reviewRepository.markAssignmentInProgress(
                            assignmentId,
                            assignment.assignmentVersion(),
                            operator.userId(),
                            operator.userUuid(),
                            now
                    ),
                    "Review assignment changed, please retry"
            );
        }
        ReviewVO.ReviewSheet sheet = reviewRepository.findSheet(sheetId, assignmentId)
                .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Review sheet could not be reloaded"));
        sheet.setScores(reviewRepository.listScoreItems(sheetId));
        return sheet;
    }

    private BigDecimal validateAndCalculateScore(
            ReviewRepository.AssignmentContext assignment,
            ReviewDTO.ReviewSheetRequest request
    ) {
        if (request == null || request.getScores() == null || request.getScores().isEmpty()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "At least one criterion score is required");
        }
        Map<Long, ReviewVO.Criterion> criteriaById = new LinkedHashMap<>();
        for (ReviewVO.Criterion criterion : assignment.criteria()) {
            criteriaById.put(criterion.getId(), criterion);
        }
        if (criteriaById.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "The locked review criteria are missing");
        }
        Set<Long> supplied = new HashSet<>();
        BigDecimal normalizedTotal = BigDecimal.ZERO;
        for (ReviewDTO.ScoreItemRequest score : request.getScores()) {
            if (score == null || score.getCriterionId() == null || score.getCriterionId() <= 0
                    || score.getScore() == null || score.getScore().compareTo(BigDecimal.ZERO) < 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Criterion scores are incomplete");
            }
            if (!supplied.add(score.getCriterionId())) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Criterion scores must be unique");
            }
            ReviewVO.Criterion criterion = criteriaById.get(score.getCriterionId());
            if (criterion == null) {
                throw biz(ErrorCode.VALIDATION_ERROR, "A score does not belong to the locked criteria version");
            }
            if (score.getScore().compareTo(criterion.getMaximumScore()) > 0) {
                throw biz(
                        ErrorCode.VALIDATION_ERROR,
                        "Score exceeds maximum for criterion " + criterion.getCriterionCode()
                );
            }
            BigDecimal contribution = score.getScore()
                    .divide(criterion.getMaximumScore(), 12, RoundingMode.HALF_UP)
                    .multiply(criterion.getWeight());
            normalizedTotal = normalizedTotal.add(contribution);
        }
        for (ReviewVO.Criterion criterion : assignment.criteria()) {
            if (Boolean.TRUE.equals(criterion.getRequired()) && !supplied.contains(criterion.getId())) {
                throw biz(
                        ErrorCode.VALIDATION_ERROR,
                        "Required criterion is missing: " + criterion.getCriterionCode()
                );
            }
        }
        BigDecimal scoreScale = assignment.scoreScale() == null
                ? new BigDecimal("100.00")
                : assignment.scoreScale();
        return normalizedTotal.multiply(scoreScale).setScale(4, RoundingMode.HALF_UP);
    }

    @Transactional
    public List<ReviewVO.Aggregate> aggregateBatch(CurrentUser currentUser, Long batchId) {
        Operator operator = requirePermission(currentUser, RESULT_AGGREGATE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!Set.of("IN_REVIEW", "AGGREGATING").contains(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only in-review batches can be aggregated");
        }
        ReviewVO.Plan plan = requirePlan(batch.getPlanId());
        int belowMinimum = reviewRepository.countCandidatesBelowMinimumSubmitted(
                batchId,
                plan.getMinimumSubmittedCount()
        );
        if (belowMinimum > 0) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    belowMinimum + " candidates have fewer than the minimum submitted reviews"
            );
        }
        if ("IN_REVIEW".equals(batch.getStatus())) {
            ReviewBatchStatus.IN_REVIEW.requireTransitionTo(ReviewBatchStatus.AGGREGATING);
            requireWrite(
                    reviewRepository.markBatchAggregating(
                            batchId,
                            batch.getVersion(),
                            operator.userId(),
                            operator.userUuid(),
                            LocalDateTime.now()
                    ),
                    "Review batch changed, please retry"
            );
        }
        List<ReviewRepository.AggregationSource> sources = reviewRepository.loadAggregationSources(batchId);
        if (sources.size() != batch.getCandidateCount()) {
            throw biz(ErrorCode.BIZ_ERROR, "Frozen candidate set is incomplete");
        }
        List<CalculatedAggregate> calculated = new ArrayList<>();
        for (ReviewRepository.AggregationSource source : sources) {
            calculated.add(calculateAggregate(source, plan));
        }
        calculated.sort(
                Comparator.comparing(CalculatedAggregate::score, Comparator.reverseOrder())
                        .thenComparing(CalculatedAggregate::candidateId)
        );
        LocalDateTime now = LocalDateTime.now();
        BigDecimal previousScore = null;
        int previousRank = 0;
        for (int index = 0; index < calculated.size(); index += 1) {
            CalculatedAggregate aggregate = calculated.get(index);
            int rank = previousScore != null && previousScore.compareTo(aggregate.score()) == 0
                    ? previousRank
                    : index + 1;
            previousScore = aggregate.score();
            previousRank = rank;
            int written = reviewRepository.upsertAggregate(
                    batchId,
                    aggregate.candidateId(),
                    aggregate.score(),
                    aggregate.minimum(),
                    aggregate.maximum(),
                    aggregate.standardDeviation(),
                    aggregate.submittedCount(),
                    aggregate.validCount(),
                    rank,
                    json(aggregate.anomalyFlags()),
                    operator.userId(),
                    operator.userUuid(),
                    now
            );
            if (written < 1) {
                throw biz(ErrorCode.BIZ_ERROR, "Review aggregate changed, please retry");
            }
        }
        return reviewRepository.listAggregates(batchId);
    }

    @Transactional
    public ReviewVO.Aggregate decideCandidate(
            CurrentUser currentUser,
            Long batchId,
            Long candidateId,
            ReviewDTO.AggregateDecisionRequest request
    ) {
        Operator operator = requirePermission(currentUser, RESULT_FINALIZE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!"AGGREGATING".equals(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Decisions can only be recorded while aggregating");
        }
        if (candidateId == null || candidateId <= 0 || request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Candidate and decision are required");
        }
        String decision = normalizeEnum(
                request.getDecision(),
                null,
                REVIEW_DECISIONS,
                "Invalid review decision"
        );
        ReviewVO.Aggregate current = reviewRepository.listAggregates(batchId).stream()
                .filter(item -> candidateId.equals(item.getCandidateId()))
                .findFirst()
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review aggregate not found"));
        String decisionReason = StringUtils.hasText(request.getReason())
                ? request.getReason().trim()
                : null;
        if (StringUtils.hasText(current.getAnomalyFlagsJson())
                && !"[]".equals(current.getAnomalyFlagsJson().trim())
                && !StringUtils.hasText(decisionReason)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "An arbitration reason is required for anomalous scores");
        }
        requireWrite(
                reviewRepository.updateAggregateDecision(
                        batchId,
                        candidateId,
                        decision,
                        decisionReason,
                        operator.userId(),
                        operator.userUuid(),
                        LocalDateTime.now()
                ),
                "Review aggregate not found or changed"
        );
        return reviewRepository.listAggregates(batchId).stream()
                .filter(item -> candidateId.equals(item.getCandidateId()))
                .findFirst()
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review aggregate not found"));
    }

    @Transactional
    public ReviewVO.Batch finalizeBatch(CurrentUser currentUser, Long batchId) {
        Operator operator = requirePermission(currentUser, RESULT_FINALIZE);
        ReviewVO.Batch batch = requireBatch(batchId);
        if ("FINALIZED".equals(batch.getStatus()) || "PUBLISHED".equals(batch.getStatus())) {
            return batch;
        }
        if (!"AGGREGATING".equals(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only aggregated review batches can be finalized");
        }
        List<ReviewVO.Aggregate> aggregates = reviewRepository.listAggregates(batchId);
        if (aggregates.size() != batch.getCandidateCount()) {
            throw biz(ErrorCode.BIZ_ERROR, "Review aggregates are incomplete");
        }
        int pendingDecisions = reviewRepository.countPendingAggregateDecisions(batchId);
        if (pendingDecisions > 0) {
            throw biz(ErrorCode.BIZ_ERROR, pendingDecisions + " review decisions are still pending");
        }
        LocalDateTime now = LocalDateTime.now();
        int finalized = reviewRepository.finalizeAggregates(
                batchId,
                operator.userId(),
                operator.userUuid(),
                now
        );
        if (finalized != batch.getCandidateCount()) {
            throw biz(ErrorCode.BIZ_ERROR, "Review aggregates changed, please retry");
        }
        ReviewBatchStatus.AGGREGATING.requireTransitionTo(ReviewBatchStatus.FINALIZED);
        requireWrite(
                reviewRepository.markBatchFinalized(
                        batchId,
                        batch.getVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        now
                ),
                "Review batch changed, please retry"
        );
        return requireBatch(batchId);
    }

    @Transactional
    public ReviewVO.Publication publishBatch(CurrentUser currentUser, Long batchId) {
        Operator operator = requirePermission(currentUser, RESULT_PUBLISH);
        ReviewVO.Batch batch = requireBatch(batchId);
        if ("PUBLISHED".equals(batch.getStatus())) {
            return reviewRepository.findLatestPublication(batchId)
                    .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Published result snapshot is missing"));
        }
        if (!"FINALIZED".equals(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only finalized review batches can be published");
        }
        List<ReviewRepository.PublicationRow> rows = reviewRepository.loadPublicationRows(batchId);
        if (rows.size() != batch.getCandidateCount()) {
            throw biz(ErrorCode.BIZ_ERROR, "Finalized review results are incomplete");
        }
        LocalDateTime publishedAt = LocalDateTime.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("batchId", batchId);
        payload.put("competitionId", batch.getCompetitionId());
        payload.put("stageId", batch.getStageId());
        payload.put("publishedAt", publishedAt);
        payload.put("results", rows);
        String payloadJson = json(payload);
        int publicationVersion = reviewRepository.findLatestPublicationVersion(batchId) + 1;
        Long publicationId = reviewRepository.insertPublication(
                batchId,
                publicationVersion,
                payloadJson,
                sha256Text(payloadJson),
                operator.userId(),
                operator.userUuid(),
                publishedAt
        );
        requireInserted(publicationId, "Review publication changed, please retry");
        for (ReviewRepository.PublicationRow row : rows) {
            int projected = reviewRepository.projectLegacyResult(
                    batch,
                    row,
                    operator.userId(),
                    operator.userUuid(),
                    publishedAt
            );
            if (projected < 1) {
                throw biz(ErrorCode.BIZ_ERROR, "Legacy review result projection failed");
            }
        }
        ReviewBatchStatus.FINALIZED.requireTransitionTo(ReviewBatchStatus.PUBLISHED);
        requireWrite(
                reviewRepository.markBatchPublished(
                        batchId,
                        batch.getVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        publishedAt
                ),
                "Review batch changed, please retry"
        );
        publishReviewResultEvent(operator, batch, publicationId, publicationVersion, rows.size());
        for (ReviewRepository.PublicationRow row : rows) {
            publishTeamReviewResultEvent(
                    operator,
                    batch,
                    publicationId,
                    publicationVersion,
                    publishedAt,
                    row
            );
        }
        increment(publicationCounter);
        increment(publishedResultCounter, rows.size());
        return reviewRepository.findLatestPublication(batchId)
                .orElseThrow(() -> biz(ErrorCode.BIZ_ERROR, "Review publication could not be reloaded"));
    }

    @Transactional
    public ReviewVO.Batch reopenPublishedBatchForCorrection(
            CurrentUser currentUser,
            Long batchId,
            ReviewDTO.PublicationCorrectionRequest request
    ) {
        Operator operator = requirePermission(currentUser, RESULT_PUBLISH);
        ReviewVO.Batch batch = requireBatch(batchId);
        if (!"PUBLISHED".equals(batch.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only published review batches can enter correction");
        }
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Publication correction reason is required");
        }
        String reason = request.getReason().trim();
        LocalDateTime now = LocalDateTime.now();
        requireWrite(
                reviewRepository.revokeLatestPublication(
                        batchId,
                        reason,
                        operator.userId(),
                        operator.userUuid(),
                        now
                ),
                "Review publication changed, please retry"
        );
        int reopenedAggregates = reviewRepository.reopenFinalizedAggregates(
                batchId,
                operator.userId(),
                operator.userUuid(),
                now
        );
        if (reopenedAggregates != batch.getCandidateCount()) {
            throw biz(ErrorCode.BIZ_ERROR, "Finalized review results are incomplete");
        }
        if (reviewRepository.markLegacyProjectionCorrectionPending(
                batch,
                reason,
                operator.userId(),
                operator.userUuid(),
                now
        ) < 1) {
            throw biz(ErrorCode.BIZ_ERROR, "Legacy review result projection is missing");
        }
        requireWrite(
                reviewRepository.reopenPublishedBatch(
                        batchId,
                        batch.getVersion(),
                        operator.userId(),
                        operator.userUuid(),
                        now
                ),
                "Review batch changed, please retry"
        );
        ReviewVO.Batch reopened = requireBatch(batchId);
        publishCorrectionEvent(operator, reopened, reason);
        return reopened;
    }

    private CalculatedAggregate calculateAggregate(
            ReviewRepository.AggregationSource source,
            ReviewVO.Plan plan
    ) {
        List<ReviewRepository.WeightedScore> submittedWeighted =
                new ArrayList<>(source.submittedScores());
        submittedWeighted.sort(Comparator.comparing(ReviewRepository.WeightedScore::score));
        List<BigDecimal> submitted = submittedWeighted.stream()
                .map(ReviewRepository.WeightedScore::score)
                .toList();
        if (submitted.size() < plan.getMinimumSubmittedCount()) {
            throw biz(ErrorCode.BIZ_ERROR, "Review submissions changed during aggregation");
        }
        List<ReviewRepository.WeightedScore> validWeighted = new ArrayList<>(submittedWeighted);
        if ("TRIMMED_MEAN".equals(plan.getAggregateMethod())) {
            int from = plan.getTrimLowestCount();
            int to = validWeighted.size() - plan.getTrimHighestCount();
            if (from >= to) {
                throw biz(ErrorCode.BIZ_ERROR, "Trim configuration leaves no valid review scores");
            }
            validWeighted = new ArrayList<>(validWeighted.subList(from, to));
        }
        List<BigDecimal> valid = validWeighted.stream()
                .map(ReviewRepository.WeightedScore::score)
                .toList();
        BigDecimal aggregateScore = switch (plan.getAggregateMethod()) {
            case "MEDIAN" -> median(valid);
            case "WEIGHTED_AVERAGE" -> weightedAverage(validWeighted);
            default -> arithmeticAverage(valid);
        };
        double mean = arithmeticAverage(valid).doubleValue();
        double variance = valid.stream()
                .mapToDouble(value -> {
                    double difference = value.doubleValue() - mean;
                    return difference * difference;
                })
                .average()
                .orElse(0D);
        BigDecimal stddev = BigDecimal.valueOf(Math.sqrt(variance)).setScale(4, RoundingMode.HALF_UP);
        List<String> flags = new ArrayList<>();
        BigDecimal spread = submitted.get(submitted.size() - 1).subtract(submitted.get(0));
        BigDecimal scale = plan.getScoreScale() == null ? new BigDecimal("100") : plan.getScoreScale();
        if (spread.compareTo(scale.multiply(new BigDecimal("0.20"))) > 0) {
            flags.add("HIGH_SCORE_SPREAD");
        }
        if (stddev.compareTo(scale.multiply(new BigDecimal("0.12"))) > 0) {
            flags.add("HIGH_SCORE_STDDEV");
        }
        if (valid.size() != submitted.size()) {
            flags.add("TRIMMED_SCORES");
        }
        return new CalculatedAggregate(
                source.candidateId(),
                aggregateScore,
                submitted.get(0),
                submitted.get(submitted.size() - 1),
                stddev,
                submitted.size(),
                valid.size(),
                List.copyOf(flags)
        );
    }

    private BigDecimal arithmeticAverage(List<BigDecimal> scores) {
        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scores.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal median(List<BigDecimal> scores) {
        int middle = scores.size() / 2;
        if (scores.size() % 2 == 1) {
            return scores.get(middle).setScale(4, RoundingMode.HALF_UP);
        }
        return scores.get(middle - 1)
                .add(scores.get(middle))
                .divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedAverage(List<ReviewRepository.WeightedScore> scores) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ReviewRepository.WeightedScore score : scores) {
            if (score.weight() == null || score.weight().compareTo(BigDecimal.ZERO) <= 0) {
                throw biz(ErrorCode.BIZ_ERROR, "Reviewer weights must be positive");
            }
            weightedSum = weightedSum.add(score.score().multiply(score.weight()));
            totalWeight = totalWeight.add(score.weight());
        }
        return weightedSum.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private void publishReviewResultEvent(
            Operator operator,
            ReviewVO.Batch batch,
            Long publicationId,
            int publicationVersion,
            int resultCount
    ) {
        if (platformEventPublisher == null) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userUuid", operator.userUuid());
        attributes.put("batchId", batch.getId());
        attributes.put("competitionId", batch.getCompetitionId());
        attributes.put("stageId", batch.getStageId());
        attributes.put("publicationVersion", publicationVersion);
        attributes.put("resultCount", resultCount);
        platformEventPublisher.record(
                "SYSTEM",
                ReviewIntegrationEvents.RESULTS_PUBLISHED,
                operator.userId(),
                "competition.review-publication",
                publicationId,
                attributes
        );
    }

    private void publishTeamReviewResultEvent(
            Operator operator,
            ReviewVO.Batch batch,
            Long publicationId,
            int publicationVersion,
            LocalDateTime publishedAt,
            ReviewRepository.PublicationRow row
    ) {
        if (platformEventPublisher == null) {
            return;
        }
        if (row.ownerUserId() == null
                || row.ownerUserId() <= 0
                || !StringUtils.hasText(row.ownerUserUuid())) {
            throw biz(ErrorCode.BIZ_ERROR, "Review result recipient identity is missing");
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userUuid", operator.userUuid());
        attributes.put("publicationId", publicationId);
        attributes.put("publicationVersion", publicationVersion);
        attributes.put("publishedAt", publishedAt);
        attributes.put("batchId", batch.getId());
        attributes.put("competitionId", batch.getCompetitionId());
        attributes.put("stageId", batch.getStageId());
        attributes.put("registrationId", row.registrationId());
        attributes.put("recipientUserId", row.ownerUserId());
        attributes.put("recipientUserUuid", row.ownerUserUuid());
        attributes.put("decision", row.decision());
        attributes.put("aggregateScore", row.aggregateScore());
        attributes.put("rankNo", row.rankNo());
        platformEventPublisher.record(
                "SYSTEM",
                RESULT_PUBLISHED_EVENT,
                operator.userId(),
                "competition.review-result.v" + publicationVersion,
                row.registrationId(),
                attributes
        );
    }

    private void publishAppealEvent(
            Operator operator,
            ReviewVO.Appeal appeal,
            String eventType
    ) {
        if (platformEventPublisher == null) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userUuid", operator.userUuid());
        attributes.put("appealNo", appeal.getAppealNo());
        attributes.put("publicationId", appeal.getPublicationId());
        attributes.put("batchId", appeal.getBatchId());
        attributes.put("registrationId", appeal.getRegistrationId());
        attributes.put("status", appeal.getStatus());
        platformEventPublisher.record(
                "SYSTEM",
                eventType,
                operator.userId(),
                "competition.review-appeal",
                appeal.getId(),
                attributes
        );
    }

    private void increment(Counter counter) {
        increment(counter, 1);
    }

    private void increment(Counter counter, int amount) {
        if (counter != null && amount > 0) {
            counter.increment(amount);
        }
    }

    private boolean stageBelongsToCompetition(Long competitionId, Long stageId) {
        if (registrationReviewInternalApi != null) {
            return registrationReviewInternalApi.stageBelongsToCompetition(competitionId, stageId);
        }
        return reviewRepository.stageBelongsToCompetition(competitionId, stageId);
    }

    private List<ReviewRepository.CandidateSnapshot> loadCandidateSnapshots(
            Long competitionId,
            List<Long> registrationIds
    ) {
        if (registrationReviewInternalApi == null) {
            return reviewRepository.loadCandidateSnapshots(competitionId, registrationIds);
        }
        return registrationReviewInternalApi
                .loadEligibleCandidateSnapshots(competitionId, registrationIds)
                .stream()
                .map(this::candidateSnapshot)
                .toList();
    }

    private ReviewRepository.CandidateSnapshot candidateSnapshot(RegistrationCandidateSnapshotDTO source) {
        return new ReviewRepository.CandidateSnapshot(
                source.registrationId(),
                source.registrationNo(),
                source.competitionId(),
                source.teamId(),
                source.projectId(),
                source.ownerUserId(),
                source.ownerUserUuid(),
                source.status(),
                source.registrationSnapshotJson(),
                source.teamSnapshotJson(),
                source.projectSnapshotJson(),
                source.memberSnapshotJson(),
                source.collectionSchemaSnapshotJson(),
                source.materialSnapshotJson()
        );
    }

    private void publishCorrectionEvent(
            Operator operator,
            ReviewVO.Batch batch,
            String reason
    ) {
        if (platformEventPublisher == null) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userUuid", operator.userUuid());
        attributes.put("batchId", batch.getId());
        attributes.put("competitionId", batch.getCompetitionId());
        attributes.put("stageId", batch.getStageId());
        attributes.put("reason", reason);
        platformEventPublisher.record(
                "SYSTEM",
                "COMPETITION_REVIEW_PUBLICATION_CORRECTION_STARTED",
                operator.userId(),
                "competition.review-batch",
                batch.getId(),
                attributes
        );
    }

    private void requireEligibleAssignmentTarget(ReviewRepository.AssignmentTarget target) {
        if (!"FROZEN".equals(target.candidateStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Review candidate is not frozen");
        }
        if (!"active".equalsIgnoreCase(target.expertStatus())
                || !"APPROVED".equalsIgnoreCase(target.approvalStatus())
                || !"ENABLED".equalsIgnoreCase(target.accountStatus())
                || target.expertUserId() == null
                || target.expertUserId() <= 0
                || !StringUtils.hasText(target.expertUserUuid())) {
            throw biz(ErrorCode.BIZ_ERROR, "Expert is not approved with an enabled linked account");
        }
        if (target.identityConflict()
                || target.expertUserId().equals(target.candidateOwnerUserId())
                || target.expertUserUuid().equals(target.candidateOwnerUserUuid())) {
            throw biz(ErrorCode.BIZ_ERROR, "Expert has an identity conflict with the candidate");
        }
    }

    private int reviewerCount(ReviewVO.Batch batch) {
        if (batch == null) {
            return DEFAULT_REVIEWER_COUNT_PER_CANDIDATE;
        }
        Integer configured = batch.getReviewerCountPerCandidate();
        return configured == null || configured <= 0
                ? defaultInt(batch.getMinimumReviewerCount(), DEFAULT_REVIEWER_COUNT_PER_CANDIDATE)
                : configured;
    }

    private int expertMinAssignments(ReviewVO.Batch batch) {
        return batch == null || batch.getExpertMinAssignments() == null
                ? DEFAULT_EXPERT_MIN_ASSIGNMENTS : batch.getExpertMinAssignments();
    }

    private int expertTargetAssignments(ReviewVO.Batch batch) {
        return batch == null || batch.getExpertTargetAssignments() == null
                ? DEFAULT_EXPERT_TARGET_ASSIGNMENTS : batch.getExpertTargetAssignments();
    }

    private int expertMaxAssignments(ReviewVO.Batch batch) {
        return batch == null || batch.getExpertMaxAssignments() == null
                ? DEFAULT_EXPERT_MAX_ASSIGNMENTS : batch.getExpertMaxAssignments();
    }

    private void validateAssignmentAllocation(Long batchId, ReviewVO.Batch batch) {
        List<Long> rosterIds = reviewRepository.listSelectedRosterExpertIds(batchId);
        if (rosterIds == null || rosterIds.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "Select the complete reviewer roster before confirming assignments");
        }
        int min = expertMinAssignments(batch);
        int max = expertMaxAssignments(batch);
        int target = expertTargetAssignments(batch);
        if (min > target || target > max || max <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Expert workload must satisfy min <= target <= max");
        }
        List<ReviewVO.Candidate> candidates = reviewRepository.listCandidates(batchId);
        if (candidates == null || candidates.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "The review batch has no frozen candidates");
        }
        Set<Long> candidateIds = candidates.stream()
                .map(ReviewVO.Candidate::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        int candidateCount = candidateIds.size();
        int expectedTaskCount = candidateCount * reviewerCount(batch);
        int minimumCapacity = rosterIds.size() * min;
        int maximumCapacity = rosterIds.size() * max;
        if (expectedTaskCount < minimumCapacity || expectedTaskCount > maximumCapacity) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    "Total review tasks " + expectedTaskCount
                            + " cannot fit the selected experts' workload range "
                            + minimumCapacity + "-" + maximumCapacity
            );
        }
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Integer> candidateAssignmentCounts = new HashMap<>();
        Set<Long> rosterSet = new HashSet<>(rosterIds);
        int activeTaskCount = 0;
        List<ReviewVO.AdminAssignment> assignments = reviewRepository.listAssignments(batchId);
        for (ReviewVO.AdminAssignment assignment : assignments == null ? List.<ReviewVO.AdminAssignment>of() : assignments) {
            if (Set.of("DECLINED", "EXPIRED", "REVOKED").contains(assignment.getStatus())) {
                continue;
            }
            if (!candidateIds.contains(assignment.getCandidateId())) {
                throw biz(ErrorCode.BIZ_ERROR, "Every assignment must belong to a frozen candidate in this batch");
            }
            if (!rosterSet.contains(assignment.getExpertId())) {
                throw biz(ErrorCode.BIZ_ERROR, "Every assignment must belong to the selected reviewer roster");
            }
            counts.merge(assignment.getExpertId(), 1, Integer::sum);
            candidateAssignmentCounts.merge(assignment.getCandidateId(), 1, Integer::sum);
            activeTaskCount++;
        }
        if (activeTaskCount != expectedTaskCount) {
            throw biz(
                    ErrorCode.BIZ_ERROR,
                    "Assignments are incomplete: expected " + expectedTaskCount + " tasks but found " + activeTaskCount
            );
        }
        int requiredPerCandidate = reviewerCount(batch);
        for (Long candidateId : candidateIds) {
            int count = candidateAssignmentCounts.getOrDefault(candidateId, 0);
            if (count != requiredPerCandidate) {
                throw biz(
                        ErrorCode.BIZ_ERROR,
                        "Candidate " + candidateId + " has " + count
                                + " projects assigned; exactly " + requiredPerCandidate + " reviewers are required"
                );
            }
        }
        for (Long expertId : rosterIds) {
            int count = counts.getOrDefault(expertId, 0);
            if (count < min || count > max) {
                throw biz(
                        ErrorCode.BIZ_ERROR,
                        "Expert " + expertId + " has " + count + " projects; allowed range is " + min + "-" + max
                );
            }
        }
    }

    private List<Long> normalizeExpertIds(List<Long> expertIds) {
        if (expertIds == null || expertIds.isEmpty()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "At least one reviewer must be selected");
        }
        Set<Long> normalized = new java.util.LinkedHashSet<>();
        for (Long expertId : expertIds) {
            if (expertId == null || expertId <= 0 || !normalized.add(expertId)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Expert ids must be positive and unique");
            }
        }
        return List.copyOf(normalized);
    }

    private List<Long> selectedRosterExpertIds(Long batchId) {
        List<Long> expertIds = reviewRepository.listSelectedRosterExpertIds(batchId);
        return expertIds == null ? List.of() : expertIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(expertId -> expertId > 0)
                .distinct()
                .toList();
    }

    private ReviewRepository.InvitationContext requireInvitationContext(String rawToken, boolean requireCheckIn) {
        if (!StringUtils.hasText(rawToken)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Review invitation token is required");
        }
        ReviewRepository.InvitationContext context = reviewRepository.findInvitationByTokenHash(
                sha256Text(rawToken.trim())
        ).orElseThrow(() -> biz(ErrorCode.UNAUTHORIZED, "Review invitation is invalid or has been replaced"));
        if (!invitationMatchesEligibleExpert(context)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Review invitation is invalid or has been replaced");
        }
        LocalDateTime now = LocalDateTime.now();
        if (context.tokenExpiresAt() == null || !context.tokenExpiresAt().isAfter(now)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Review invitation has expired");
        }
        if (!Set.of("SENT", "OPENED", "QR_ISSUED", "CHECKED_IN").contains(context.invitationStatus())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Review invitation is not available for use");
        }
        if (requireCheckIn && context.checkedInAt() == null) {
            throw biz(ErrorCode.FORBIDDEN, "The expert must complete administrator check-in first");
        }
        if (context.expertUserId() == null || context.expertUserId() <= 0
                || !StringUtils.hasText(context.expertUserUuid())) {
            throw biz(ErrorCode.BIZ_ERROR, "The invitation is not linked to an enabled expert account");
        }
        return context;
    }

    private ReviewVO.Invitation toInvitation(
            ReviewRepository.InvitationContext context,
            String qrValue
    ) {
        ReviewVO.Invitation invitation = new ReviewVO.Invitation();
        invitation.setInvitationId(context.invitationId());
        invitation.setBatchId(context.batchId());
        invitation.setBatchName(context.batchName());
        invitation.setExpertId(context.expertId());
        invitation.setExpertName(context.expertName());
        invitation.setStatus(context.invitationStatus());
        invitation.setDeliveryStatus(
                Set.of("SENT", "OPENED", "QR_ISSUED", "CHECKED_IN").contains(context.invitationStatus())
                        ? "SENT" : context.invitationStatus()
        );
        invitation.setCheckinStatus(context.checkedInAt() == null ? "WAITING" : "CHECKED_IN");
        invitation.setQrValue(qrValue);
        invitation.setQrExpiresAt(context.qrExpiresAt());
        invitation.setTokenExpiresAt(context.tokenExpiresAt());
        invitation.setCheckedInAt(context.checkedInAt());
        invitation.setSentAt(context.sentAt());
        invitation.setFailureReason(context.failureReason());
        return invitation;
    }

    private void recordRejectedCheckin(
            Operator operator,
            Long batchId,
            ReviewRepository.InvitationContext context,
            String qrTokenHash,
            String reason
    ) {
        reviewRepository.recordCheckinAttempt(
                batchId,
                context.invitationId(),
                context.expertId(),
                qrTokenHash,
                "REJECTED",
                reason,
                operator.userId(),
                operator.userUuid(),
                LocalDateTime.now()
        );
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildInvitationContent(
            ReviewVO.Batch batch,
            ReviewVO.RosterExpert reviewer,
            String invitationUrl,
            LocalDateTime expiresAt
    ) {
        return "您好，" + (reviewer.getExpertName() == null ? "专家" : reviewer.getExpertName()) + "：\n\n"
                + "您已被邀请参加评审批次《" + batch.getBatchName() + "》。\n"
                + "请在现场打开以下专属链接，展示签到二维码，由管理员扫码后开始评审：\n"
                + invitationUrl + "\n\n"
                + "链接有效期至：" + expiresAt + "。旧链接在重新发送邀请后立即失效。\n"
                + "请勿将链接转发给其他人员。";
    }

    private void recordInvitationDispatchEvent(
            Operator operator,
            ReviewVO.Batch batch,
            int sentCount,
            int totalCount
    ) {
        if (platformEventPublisher == null) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userUuid", operator.userUuid());
        attributes.put("batchId", batch.getId());
        attributes.put("sentCount", sentCount);
        attributes.put("totalCount", totalCount);
        platformEventPublisher.record(
                "SYSTEM",
                ReviewIntegrationEvents.INVITATIONS_DISPATCHED,
                operator.userId(),
                "competition.review-batch",
                batch.getId(),
                attributes
        );
    }

    private boolean isEligibleAssignmentTarget(ReviewRepository.AssignmentTarget target) {
        return "FROZEN".equals(target.candidateStatus())
                && "active".equalsIgnoreCase(target.expertStatus())
                && "APPROVED".equalsIgnoreCase(target.approvalStatus())
                && "ENABLED".equalsIgnoreCase(target.accountStatus())
                && target.expertUserId() != null
                && target.expertUserId() > 0
                && StringUtils.hasText(target.expertUserUuid())
                && !target.identityConflict()
                && !target.expertUserId().equals(target.candidateOwnerUserId())
                && !target.expertUserUuid().equals(target.candidateOwnerUserUuid());
    }

    private List<ReviewRepository.ExpertRosterCandidate> listEligibleExperts(List<Long> expertIds) {
        ExpertSnapshotPort expertSnapshotPort = expertSnapshotPortProvider == null
                ? null : expertSnapshotPortProvider.getIfAvailable();
        if (expertSnapshotPort == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Expert profile service is unavailable");
        }
        List<ReviewRepository.ExpertRosterCandidate> experts = new ArrayList<>();
        for (Long expertId : expertIds == null ? List.<Long>of() : expertIds) {
            ExpertSnapshot snapshot = expertSnapshotPort.findExpertSnapshot(expertId);
            if (snapshot == null
                    || !expertId.equals(snapshot.expertId())
                    || !isApprovedEnabledExpert(snapshot)
                    || !StringUtils.hasText(snapshot.email())) {
                continue;
            }
            experts.add(new ReviewRepository.ExpertRosterCandidate(
                    snapshot.expertId(), snapshot.userId(), snapshot.userUuid(), snapshot.name(), snapshot.email(),
                    snapshot.status(), snapshot.approvalStatus(), snapshot.accountStatus()
            ));
        }
        return List.copyOf(experts);
    }

    private List<ReviewRepository.ExpertWorkload> availableExpertWorkloads() {
        ExpertSnapshotPort expertSnapshotPort = expertSnapshotPortProvider == null
                ? null : expertSnapshotPortProvider.getIfAvailable();
        if (expertSnapshotPort == null) {
            return reviewRepository.listApprovedExpertWorkloads();
        }
        Map<Long, Integer> persistedWorkloads = new HashMap<>();
        for (ReviewRepository.ExpertWorkload workload : reviewRepository.listApprovedExpertWorkloads()) {
            if (workload != null && workload.expertId() != null) {
                persistedWorkloads.put(workload.expertId(), workload.activeAssignmentCount());
            }
        }
        List<ExpertSnapshot> snapshots = expertSnapshotPort.listApprovedForReview();
        return (snapshots == null ? List.<ExpertSnapshot>of() : snapshots).stream()
                .filter(java.util.Objects::nonNull)
                .filter(snapshot -> snapshot.expertId() != null && snapshot.expertId() > 0)
                .filter(this::isApprovedEnabledExpert)
                .map(snapshot -> new ReviewRepository.ExpertWorkload(
                        snapshot.expertId(), persistedWorkloads.getOrDefault(snapshot.expertId(), 0)
                ))
                .sorted(Comparator.comparingInt(ReviewRepository.ExpertWorkload::activeAssignmentCount)
                        .thenComparing(ReviewRepository.ExpertWorkload::expertId))
                .toList();
    }

    private ReviewRepository.AssignmentTarget attachExpertSnapshot(ReviewRepository.AssignmentTarget target) {
        ExpertSnapshotPort expertSnapshotPort = expertSnapshotPortProvider == null
                ? null : expertSnapshotPortProvider.getIfAvailable();
        if (expertSnapshotPort == null || target == null || target.expertId() == null) {
            return target;
        }
        ExpertSnapshot snapshot = expertSnapshotPort.findExpertSnapshot(target.expertId());
        if (snapshot == null) {
            return target;
        }
        return new ReviewRepository.AssignmentTarget(
                target.candidateId(), target.registrationId(), target.candidateStatus(),
                target.candidateOwnerUserId(), target.candidateOwnerUserUuid(), target.expertId(),
                snapshot.userId(), snapshot.userUuid(), snapshot.status(), snapshot.approvalStatus(),
                snapshot.accountStatus(),
                target.identityConflict() || snapshotIdentityConflict(target.candidateSnapshotJson(), snapshot.userUuid()),
                target.candidateSnapshotJson()
        );
    }

    private boolean isApprovedEnabledExpert(ExpertSnapshot snapshot) {
        return "active".equalsIgnoreCase(snapshot.status())
                && "APPROVED".equalsIgnoreCase(snapshot.approvalStatus())
                && "ENABLED".equalsIgnoreCase(snapshot.accountStatus())
                && snapshot.userId() != null
                && snapshot.userId() > 0
                && StringUtils.hasText(snapshot.userUuid());
    }

    private boolean invitationMatchesEligibleExpert(ReviewRepository.InvitationContext context) {
        ExpertSnapshotPort expertSnapshotPort = expertSnapshotPortProvider == null
                ? null : expertSnapshotPortProvider.getIfAvailable();
        if (expertSnapshotPort == null || context == null || context.expertId() == null) {
            return false;
        }
        ExpertSnapshot snapshot = expertSnapshotPort.findExpertSnapshot(context.expertId());
        return snapshot != null
                && context.expertId().equals(snapshot.expertId())
                && java.util.Objects.equals(context.expertUserId(), snapshot.userId())
                && java.util.Objects.equals(context.expertUserUuid(), snapshot.userUuid())
                && isApprovedEnabledExpert(snapshot);
    }

    private boolean snapshotIdentityConflict(String snapshotJson, String userUuid) {
        if (!StringUtils.hasText(snapshotJson) || !StringUtils.hasText(userUuid)) {
            return false;
        }
        try {
            return containsSnapshotText(objectMapper.readTree(snapshotJson), userUuid.trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean containsSnapshotText(JsonNode node, String expected) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return expected.equals(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsSnapshotText(child, expected)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            java.util.Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                if (containsSnapshotText(children.next(), expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private TeamInternalApi teamInternalApi() {
        return teamInternalApiProvider == null ? null : teamInternalApiProvider.getIfAvailable();
    }

    private List<Long> activeTeamIdsForUser(Operator operator, TeamInternalApi teamInternalApi) {
        List<Long> teamIds = teamInternalApi.listActiveTeamIdsForUser(operator.userId(), operator.userUuid());
        return teamIds == null ? List.of() : teamIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(teamId -> teamId > 0)
                .distinct()
                .toList();
    }

    private ReviewVO.Batch requireBatch(Long batchId) {
        if (batchId == null || batchId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review batch id is required");
        }
        return reviewRepository.findBatch(batchId)
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review batch not found"));
    }

    private ReviewRepository.AssignmentContext requireOwnedAssignment(Long assignmentId, Operator operator) {
        return requireOwnedAssignment(assignmentId, operator, null);
    }

    private ReviewRepository.AssignmentContext requireOwnedAssignment(
            Long assignmentId,
            Operator operator,
            Long expectedBatchId
    ) {
        if (assignmentId == null || assignmentId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review assignment id is required");
        }
        ReviewRepository.AssignmentContext assignment = reviewRepository.findOwnedAssignment(
                assignmentId,
                operator.userId(),
                operator.userUuid()
        ).orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review assignment not found"));
        if (expectedBatchId != null && !expectedBatchId.equals(assignment.batchId())) {
            throw biz(ErrorCode.NOT_FOUND, "Review assignment is outside this invitation");
        }
        return assignment;
    }

    private ReviewVO.AssignmentTask requireOwnedTask(Long assignmentId, Operator operator) {
        return reviewRepository.listOwnedAssignments(operator.userId(), operator.userUuid()).stream()
                .filter(task -> assignmentId.equals(task.getAssignmentId()))
                .findFirst()
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review assignment not found"));
    }

    private List<Long> normalizeRegistrationIds(List<Long> registrationIds) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<Long> normalized = new java.util.LinkedHashSet<>();
        for (Long registrationId : registrationIds) {
            if (registrationId == null || registrationId <= 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Registration ids must be positive");
            }
            normalized.add(registrationId);
        }
        if (normalized.size() != registrationIds.size()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration ids must be unique");
        }
        return List.copyOf(normalized);
    }

    private String immutableCandidateSnapshot(ReviewRepository.CandidateSnapshot source) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("registrationId", source.registrationId());
        snapshot.put("registrationNo", source.registrationNo());
        snapshot.put("competitionId", source.competitionId());
        snapshot.put("teamId", source.teamId());
        snapshot.put("projectId", source.projectId());
        snapshot.put("ownerUserId", source.ownerUserId());
        snapshot.put("ownerUserUuid", source.ownerUserUuid());
        snapshot.put("registrationStatus", source.status());
        snapshot.put("registration", parseJson(source.registrationSnapshotJson()));
        snapshot.put("team", parseJson(source.teamSnapshotJson()));
        snapshot.put("project", parseJson(source.projectSnapshotJson()));
        snapshot.put("members", parseJson(source.memberSnapshotJson()));
        snapshot.put("collectionSchema", parseJson(source.collectionSchemaSnapshotJson()));
        snapshot.put("materials", parseJson(source.materialSnapshotJson()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize review candidate snapshot", exception);
        }
    }

    private String immutableReviewSnapshot(
            ReviewRepository.CandidateSnapshot source,
            String blindMode,
            String blindCode
    ) {
        if ("NONE".equals(blindMode)) {
            return immutableCandidateSnapshot(source);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 2);
        snapshot.put("blindCode", blindCode);
        snapshot.put("competitionId", source.competitionId());
        snapshot.put("registrationStatus", source.status());
        snapshot.put("project", blindSafeJson(source.projectSnapshotJson()));
        snapshot.put("collectionSchema", blindSafeJson(source.collectionSchemaSnapshotJson()));
        snapshot.put("materials", blindSafeJson(source.materialSnapshotJson()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize blind review candidate snapshot", exception);
        }
    }

    private JsonNode blindSafeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return sanitizeBlindNode(objectMapper.readTree(value));
        } catch (JsonProcessingException exception) {
            throw biz(ErrorCode.BIZ_ERROR, "Registration snapshot data is invalid");
        }
    }

    private JsonNode sanitizeBlindNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            JsonNode configuredKey = node.get("itemKey");
            if (configuredKey != null && configuredKey.isTextual()
                    && isBlindSensitiveKey(configuredKey.asText())) {
                return null;
            }
            var sanitized = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (isBlindSensitiveKey(entry.getKey())) {
                    return;
                }
                JsonNode child = sanitizeBlindNode(entry.getValue());
                if (child != null) {
                    sanitized.set(entry.getKey(), child);
                }
            });
            return sanitized;
        }
        if (node.isArray()) {
            var sanitized = objectMapper.createArrayNode();
            node.forEach(child -> {
                JsonNode safeChild = sanitizeBlindNode(child);
                if (safeChild != null) {
                    sanitized.add(safeChild);
                }
            });
            return sanitized;
        }
        if (node.isTextual()) {
            String text = BLIND_EMAIL_PATTERN.matcher(node.textValue()).replaceAll("[REDACTED]");
            text = BLIND_MOBILE_PATTERN.matcher(text).replaceAll("[REDACTED]");
            return objectMapper.getNodeFactory().textNode(text);
        }
        return node.deepCopy();
    }

    private boolean isBlindSensitiveKey(String key) {
        String normalized = key == null
                ? ""
                : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return BLIND_SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("userid")
                || normalized.endsWith("useruuid")
                || normalized.endsWith("email")
                || normalized.endsWith("mobile")
                || normalized.endsWith("phone")
                || normalized.endsWith("filename");
    }

    private Object parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw biz(ErrorCode.BIZ_ERROR, "Registration snapshot data is invalid");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize review data", exception);
        }
    }

    private ReviewVO.Plan requirePlan(Long planId) {
        if (planId == null || planId <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review plan id is required");
        }
        return reviewRepository.findPlan(planId)
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review plan not found"));
    }

    private List<ReviewDTO.CriterionRequest> requireValidCriteria(List<ReviewDTO.CriterionRequest> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "At least one review criterion is required");
        }
        if (criteria.size() > 100) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many review criteria");
        }
        Set<String> codes = new HashSet<>();
        for (ReviewDTO.CriterionRequest criterion : criteria) {
            if (criterion == null || !StringUtils.hasText(criterion.getCode())
                    || !StringUtils.hasText(criterion.getName())
                    || criterion.getWeight() == null
                    || criterion.getWeight().compareTo(BigDecimal.ZERO) <= 0
                    || criterion.getMaximumScore() == null
                    || criterion.getMaximumScore().compareTo(BigDecimal.ZERO) <= 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Review criterion is incomplete");
            }
            String normalizedCode = criterion.getCode().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(normalizedCode)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Review criterion codes must be unique");
            }
            criterion.setCode(normalizedCode);
        }
        return List.copyOf(criteria);
    }

    private Operator requirePermission(CurrentUser currentUser, String permission) {
        if (currentUser == null || currentUser.getUserId() == null || currentUser.getUserId() <= 0
                || !StringUtils.hasText(currentUser.getUserUuid())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (!permissions.contains("*") && !permissions.contains(permission)) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permission);
        }
        return new Operator(currentUser.getUserId(), currentUser.getUserUuid().trim());
    }

    private String sha256(Object value) {
        try {
            byte[] payload = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return sha256(payload);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash review criteria", exception);
        }
    }

    private String sha256Text(String value) {
        try {
            return sha256(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash review candidate snapshot", exception);
        }
    }

    private String sha256(byte[] payload) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeEnum(String value, String fallback, Set<String> supported, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!supported.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private void requireOptionalPositiveId(Long id, String message) {
        if (id != null && id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requireInserted(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private void requireWrite(int changed, String message) {
        if (changed != 1) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private BizException biz(ErrorCode code, String message) {
        return new BizException(code, message);
    }

    private record Operator(Long userId, String userUuid) {
    }

    private record CalculatedAggregate(
            Long candidateId,
            BigDecimal score,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal standardDeviation,
            int submittedCount,
            int validCount,
            List<String> anomalyFlags
    ) {
    }
}
