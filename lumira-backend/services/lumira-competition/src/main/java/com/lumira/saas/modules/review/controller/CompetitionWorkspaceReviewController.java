package com.lumira.saas.modules.review.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.competition.app.CompetitionAccessDecision;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionCapability;
import com.lumira.saas.modules.competition.app.CompetitionRef;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.review.app.ReviewAppService;
import com.lumira.saas.modules.review.dto.ReviewDTO;
import com.lumira.saas.modules.review.repository.ReviewRepository;
import com.lumira.saas.modules.review.vo.ReviewVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UUID-scoped review workbench boundary. */
@RestController
@RequestMapping("/api/v2/aiadc/competitions/{competitionUuid}/reviews")
public class CompetitionWorkspaceReviewController {
    private final ReviewAppService reviewAppService;
    private final ReviewRepository reviewRepository;
    private final CompetitionWorkspaceAccessPolicy accessPolicy;
    private final SecurityContextFacade securityContextFacade;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;

    @Autowired
    public CompetitionWorkspaceReviewController(
            ReviewAppService reviewAppService,
            ReviewRepository reviewRepository,
            CompetitionWorkspaceAccessPolicy accessPolicy,
            SecurityContextFacade securityContextFacade,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this.reviewAppService = reviewAppService;
        this.reviewRepository = reviewRepository;
        this.accessPolicy = accessPolicy;
        this.securityContextFacade = securityContextFacade;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
    }

    @GetMapping("/plans")
    public ApiResponse<List<ReviewVO.Plan>> plans(
            @PathVariable String competitionUuid,
            @RequestParam(value = "stageId", required = false) Long stageId
    ) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        return success(reviewAppService.listPlans(user, competition.id(), stageId));
    }

    @PostMapping("/plans")
    public ApiResponse<ReviewVO.Plan> createPlan(
            @PathVariable String competitionUuid,
            @Valid @RequestBody ReviewDTO.PlanCreateRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        request.setCompetitionId(competition.id());
        return success(reviewAppService.createPlan(user, request));
    }

    @GetMapping("/plans/{planId}")
    public ApiResponse<ReviewVO.Plan> plan(
            @PathVariable String competitionUuid,
            @PathVariable Long planId
    ) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        return success(requirePlan(user, planId, competition.id()));
    }

    @PostMapping("/plans/{planId}/activate")
    public ApiResponse<ReviewVO.Plan> activatePlan(
            @PathVariable String competitionUuid,
            @PathVariable Long planId
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requirePlan(user, planId, competition.id());
        return success(requirePlan(user, reviewAppService.activatePlan(user, planId).getId(), competition.id()));
    }

    @GetMapping("/batches")
    public ApiResponse<List<ReviewVO.Batch>> batches(
            @PathVariable String competitionUuid,
            @RequestParam(value = "planId", required = false) Long planId
    ) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        if (planId != null) requirePlan(user, planId, competition.id());
        return success(reviewAppService.listBatches(user, planId, competition.id()));
    }

    @PostMapping("/batches")
    public ApiResponse<ReviewVO.Batch> createBatch(
            @PathVariable String competitionUuid,
            @Valid @RequestBody ReviewDTO.BatchCreateRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requirePlan(user, request.getPlanId(), competition.id());
        return success(reviewAppService.createBatch(user, request));
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<ReviewVO.Batch> batch(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId
    ) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        return success(requireBatch(batchId, competition.id()));
    }

    @PostMapping("/batches/{batchId}/freeze")
    public ApiResponse<ReviewVO.Batch> freeze(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId,
            @Valid @RequestBody(required = false) ReviewDTO.BatchFreezeRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.freezeBatch(user, batchId, request));
    }

    @GetMapping("/batches/{batchId}/candidates")
    public ApiResponse<List<ReviewVO.Candidate>> candidates(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.listCandidates(user, batchId));
    }

    @GetMapping("/batches/{batchId}/assignments")
    public ApiResponse<List<ReviewVO.AdminAssignment>> assignments(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.listAssignments(user, batchId));
    }

    @GetMapping("/batches/{batchId}/roster")
    public ApiResponse<List<ReviewVO.RosterExpert>> roster(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.listRoster(user, batchId));
    }

    @PutMapping("/batches/{batchId}/roster")
    public ApiResponse<List<ReviewVO.RosterExpert>> saveRoster(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId,
            @Valid @RequestBody ReviewDTO.RosterSaveRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.saveRoster(user, batchId, request));
    }

    @PostMapping("/batches/{batchId}/assignments/confirm")
    public ApiResponse<ReviewVO.Batch> confirmAssignments(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.confirmAssignments(user, batchId));
    }

    @GetMapping("/batches/{batchId}/invitations")
    public ApiResponse<List<ReviewVO.RosterExpert>> invitations(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.listInvitations(user, batchId));
    }

    @PostMapping("/batches/{batchId}/invitations")
    public ApiResponse<List<ReviewVO.RosterExpert>> sendInvitations(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.sendInvitations(user, batchId));
    }

    @PostMapping("/batches/{batchId}/check-ins")
    public ApiResponse<ReviewVO.Invitation> checkIn(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId,
            @Valid @RequestBody ReviewDTO.CheckInRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.checkIn(user, batchId, request));
    }

    @PostMapping("/batches/{batchId}/assignments")
    public ApiResponse<ReviewVO.AssignmentResult> assignExperts(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId,
            @Valid @RequestBody ReviewDTO.AssignmentCreateRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.assignExperts(user, batchId, request));
    }

    @PostMapping("/batches/{batchId}/auto-assign")
    public ApiResponse<ReviewVO.AssignmentResult> autoAssign(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId,
            @Valid @RequestBody(required = false) ReviewDTO.AutoAssignmentRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.autoAssignExperts(user, batchId, request));
    }

    @PostMapping("/batches/{batchId}/start")
    public ApiResponse<ReviewVO.Batch> start(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.startReview(user, batchId));
    }

    @GetMapping("/batches/{batchId}/aggregates")
    public ApiResponse<List<ReviewVO.Aggregate>> aggregates(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.listAggregates(user, batchId));
    }

    @PostMapping("/batches/{batchId}/aggregate")
    public ApiResponse<List<ReviewVO.Aggregate>> aggregate(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.aggregateBatch(user, batchId));
    }

    @PutMapping("/batches/{batchId}/candidates/{candidateId}/decision")
    public ApiResponse<ReviewVO.Aggregate> decide(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId,
            @PathVariable Long candidateId,
            @Valid @RequestBody ReviewDTO.AggregateDecisionRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.decideCandidate(user, batchId, candidateId, request));
    }

    @PostMapping("/batches/{batchId}/finalize")
    public ApiResponse<ReviewVO.Batch> finalizeBatch(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.finalizeBatch(user, batchId));
    }

    @PostMapping("/batches/{batchId}/publish")
    public ApiResponse<ReviewVO.Publication> publish(@PathVariable String competitionUuid, @PathVariable Long batchId) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.publishBatch(user, batchId));
    }

    @PostMapping("/batches/{batchId}/correction")
    public ApiResponse<ReviewVO.Batch> correction(
            @PathVariable String competitionUuid,
            @PathVariable Long batchId,
            @Valid @RequestBody ReviewDTO.PublicationCorrectionRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        requireBatch(batchId, competition.id());
        return success(reviewAppService.reopenPublishedBatchForCorrection(user, batchId, request));
    }

    @GetMapping("/appeals")
    public ApiResponse<List<ReviewVO.Appeal>> appeals(
            @PathVariable String competitionUuid,
            @RequestParam(value = "batchId", required = false) Long batchId,
            @RequestParam(value = "status", required = false) String status
    ) {
        CurrentUser user = requireReadUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        if (batchId != null) requireBatch(batchId, competition.id());
        List<ReviewVO.Appeal> appeals = reviewAppService.listAppeals(user, batchId, status);
        return success(appeals.stream().filter(item -> competition.id().equals(item.getCompetitionId())).toList());
    }

    @PutMapping("/appeals/{appealId}/resolution")
    public ApiResponse<ReviewVO.Appeal> resolveAppeal(
            @PathVariable String competitionUuid,
            @PathVariable Long appealId,
            @Valid @RequestBody ReviewDTO.AppealResolveRequest request
    ) {
        CurrentUser user = requireWriteUser(competitionUuid);
        CompetitionRef competition = competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        ReviewVO.Appeal appeal = reviewRepository.findAppeal(appealId)
                .filter(item -> competition.id().equals(item.getCompetitionId()))
                .orElseThrow(() -> notFound("Review appeal not found"));
        return success(reviewAppService.resolveAppeal(user, appeal.getId(), request));
    }

    private CurrentUser requireReadUser(String competitionUuid) {
        CurrentUser user = requireTrustedUser();
        competition(user, competitionUuid, CompetitionCapability.REVIEW_READ);
        return user;
    }

    private CurrentUser requireWriteUser(String competitionUuid) {
        CurrentUser user = requireTrustedUser();
        competition(user, competitionUuid, CompetitionCapability.REVIEW_MANAGE);
        return user;
    }

    private CompetitionRef competition(CurrentUser user, String competitionUuid, CompetitionCapability capability) {
        CompetitionAccessDecision decision = accessPolicy.requireAccessibleCompetition(user, competitionUuid, capability);
        return decision.competition();
    }

    private ReviewVO.Plan requirePlan(CurrentUser user, Long planId, Long competitionId) {
        if (planId == null) throw notFound("Review plan not found");
        return reviewRepository.findPlan(planId)
                .filter(item -> competitionId.equals(item.getCompetitionId()))
                .map(item -> {
                    // Keep the application service as the source of the complete plan representation.
                    return reviewAppService.getPlan(user, item.getId());
                })
                .orElseThrow(() -> notFound("Review plan not found"));
    }

    private ReviewVO.Batch requireBatch(Long batchId, Long competitionId) {
        if (batchId == null) throw notFound("Review batch not found");
        return reviewRepository.findBatch(batchId)
                .filter(item -> competitionId.equals(item.getCompetitionId()))
                .orElseThrow(() -> notFound("Review batch not found"));
    }

    private CurrentUser requireTrustedUser() {
        CurrentUser user = securityContextFacade.getCurrentUser();
        CompetitionAuthenticationTrust.refresh(user, trustedCurrentUserResolver, true);
        if (!isTrustedCurrentUser(user)) throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        return user;
    }

    private <T> ApiResponse<T> success(T value) {
        return ApiResponse.success(value, TraceContext.getRequestId());
    }

    private BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }
}
