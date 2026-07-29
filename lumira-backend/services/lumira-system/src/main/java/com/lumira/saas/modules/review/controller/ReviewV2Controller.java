package com.lumira.saas.modules.review.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.review.app.ReviewAppService;
import com.lumira.saas.modules.review.dto.ReviewDTO;
import com.lumira.saas.modules.review.vo.ReviewVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/reviews")
public class ReviewV2Controller {

    private final ReviewAppService reviewAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedSession;

    public ReviewV2Controller(
            ReviewAppService reviewAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(reviewAppService, securityContextFacade, permissionGuard, null, false);
    }

    @Autowired
    public ReviewV2Controller(
            ReviewAppService reviewAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(reviewAppService, securityContextFacade, permissionGuard, sessionAuthenticationService, true);
    }

    private ReviewV2Controller(
            ReviewAppService reviewAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedSession
    ) {
        this.reviewAppService = reviewAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedSession = enforceTrustedSession;
    }

    @PostMapping("/plans")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Plan> createPlan(@Valid @RequestBody ReviewDTO.PlanCreateRequest request) {
        CurrentUser currentUser = require(ReviewAppService.PLAN_MANAGE);
        return ApiResponse.success(reviewAppService.createPlan(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/plans/{planId}")
    public ApiResponse<ReviewVO.Plan> plan(@PathVariable("planId") Long planId) {
        CurrentUser currentUser = require(ReviewAppService.PLAN_MANAGE);
        return ApiResponse.success(reviewAppService.getPlan(currentUser, planId), TraceContext.getRequestId());
    }

    @GetMapping("/plans")
    public ApiResponse<java.util.List<ReviewVO.Plan>> plans(
            @RequestParam(value = "competitionId", required = false) Long competitionId,
            @RequestParam(value = "stageId", required = false) Long stageId
    ) {
        CurrentUser currentUser = require(ReviewAppService.PLAN_MANAGE);
        return ApiResponse.success(
                reviewAppService.listPlans(currentUser, competitionId, stageId),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/plans/{planId}/activate")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Plan> activatePlan(@PathVariable("planId") Long planId) {
        CurrentUser currentUser = require(ReviewAppService.PLAN_MANAGE);
        return ApiResponse.success(reviewAppService.activatePlan(currentUser, planId), TraceContext.getRequestId());
    }

    @PostMapping("/batches")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Batch> createBatch(@Valid @RequestBody ReviewDTO.BatchCreateRequest request) {
        CurrentUser currentUser = require(ReviewAppService.BATCH_CREATE);
        return ApiResponse.success(reviewAppService.createBatch(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/batches")
    public ApiResponse<java.util.List<ReviewVO.Batch>> batches(
            @RequestParam(value = "planId", required = false) Long planId,
            @RequestParam(value = "competitionId", required = false) Long competitionId
    ) {
        CurrentUser currentUser = require(ReviewAppService.BATCH_CREATE);
        return ApiResponse.success(
                reviewAppService.listBatches(currentUser, planId, competitionId),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<ReviewVO.Batch> batch(@PathVariable("batchId") Long batchId) {
        CurrentUser currentUser = require(ReviewAppService.BATCH_CREATE);
        return ApiResponse.success(reviewAppService.getBatch(currentUser, batchId), TraceContext.getRequestId());
    }

    @GetMapping("/batches/{batchId}/candidates")
    public ApiResponse<java.util.List<ReviewVO.Candidate>> candidates(@PathVariable("batchId") Long batchId) {
        CurrentUser currentUser = require(ReviewAppService.ASSIGNMENT_MANAGE);
        return ApiResponse.success(
                reviewAppService.listCandidates(currentUser, batchId),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/batches/{batchId}/assignments")
    public ApiResponse<java.util.List<ReviewVO.AdminAssignment>> assignments(
            @PathVariable("batchId") Long batchId
    ) {
        CurrentUser currentUser = require(ReviewAppService.ASSIGNMENT_MANAGE);
        return ApiResponse.success(
                reviewAppService.listAssignments(currentUser, batchId),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/batches/{batchId}/aggregates")
    public ApiResponse<java.util.List<ReviewVO.Aggregate>> aggregates(@PathVariable("batchId") Long batchId) {
        CurrentUser currentUser = require(ReviewAppService.RESULT_AGGREGATE);
        return ApiResponse.success(
                reviewAppService.listAggregates(currentUser, batchId),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/batches/{batchId}/publication")
    public ApiResponse<ReviewVO.Publication> publication(@PathVariable("batchId") Long batchId) {
        CurrentUser currentUser = require(ReviewAppService.RESULT_PUBLISH);
        return ApiResponse.success(
                reviewAppService.getLatestPublication(currentUser, batchId),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/freeze")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Batch> freezeBatch(
            @PathVariable("batchId") Long batchId,
            @Valid @RequestBody(required = false) ReviewDTO.BatchFreezeRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.BATCH_CREATE);
        return ApiResponse.success(
                reviewAppService.freezeBatch(currentUser, batchId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/assignments")
    @RepeatSubmit
    public ApiResponse<ReviewVO.AssignmentResult> assignExperts(
            @PathVariable("batchId") Long batchId,
            @Valid @RequestBody ReviewDTO.AssignmentCreateRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.ASSIGNMENT_MANAGE);
        return ApiResponse.success(
                reviewAppService.assignExperts(currentUser, batchId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/auto-assign")
    @RepeatSubmit
    public ApiResponse<ReviewVO.AssignmentResult> autoAssignExperts(
            @PathVariable("batchId") Long batchId,
            @Valid @RequestBody(required = false) ReviewDTO.AutoAssignmentRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.ASSIGNMENT_MANAGE);
        return ApiResponse.success(
                reviewAppService.autoAssignExperts(currentUser, batchId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/start")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Batch> startReview(@PathVariable("batchId") Long batchId) {
        CurrentUser currentUser = require(ReviewAppService.ASSIGNMENT_MANAGE);
        return ApiResponse.success(reviewAppService.startReview(currentUser, batchId), TraceContext.getRequestId());
    }

    @GetMapping("/assignments/mine")
    public ApiResponse<java.util.List<ReviewVO.AssignmentTask>> myAssignments() {
        CurrentUser currentUser = require(ReviewAppService.TASK_VIEW);
        return ApiResponse.success(reviewAppService.listMyAssignments(currentUser), TraceContext.getRequestId());
    }

    @PostMapping("/assignments/{assignmentId}/accept")
    @RepeatSubmit
    public ApiResponse<ReviewVO.AssignmentTask> acceptAssignment(
            @PathVariable("assignmentId") Long assignmentId
    ) {
        CurrentUser currentUser = require(ReviewAppService.TASK_VIEW);
        return ApiResponse.success(
                reviewAppService.acceptAssignment(currentUser, assignmentId),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/assignments/{assignmentId}/decline")
    @RepeatSubmit
    public ApiResponse<ReviewVO.AssignmentTask> declineAssignment(
            @PathVariable("assignmentId") Long assignmentId,
            @Valid @RequestBody ReviewDTO.AssignmentDeclineRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.TASK_VIEW);
        return ApiResponse.success(
                reviewAppService.declineAssignment(currentUser, assignmentId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/assignments/{assignmentId}/revoke")
    @RepeatSubmit
    public ApiResponse<ReviewVO.AdminAssignment> revokeAssignment(
            @PathVariable("batchId") Long batchId,
            @PathVariable("assignmentId") Long assignmentId,
            @Valid @RequestBody ReviewDTO.AssignmentRevokeRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.ASSIGNMENT_MANAGE);
        return ApiResponse.success(
                reviewAppService.revokeAssignment(
                        currentUser,
                        batchId,
                        assignmentId,
                        request
                ),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/assignments/{assignmentId}/sheet")
    @RepeatSubmit
    public ApiResponse<ReviewVO.ReviewSheet> saveDraft(
            @PathVariable("assignmentId") Long assignmentId,
            @Valid @RequestBody ReviewDTO.ReviewSheetRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.SCORE_SUBMIT);
        return ApiResponse.success(
                reviewAppService.saveDraft(currentUser, assignmentId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/assignments/{assignmentId}/submit")
    @RepeatSubmit
    public ApiResponse<ReviewVO.ReviewSheet> submitSheet(
            @PathVariable("assignmentId") Long assignmentId,
            @Valid @RequestBody ReviewDTO.ReviewSheetRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.SCORE_SUBMIT);
        return ApiResponse.success(
                reviewAppService.submitSheet(currentUser, assignmentId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/aggregate")
    @RepeatSubmit
    public ApiResponse<java.util.List<ReviewVO.Aggregate>> aggregateBatch(
            @PathVariable("batchId") Long batchId
    ) {
        CurrentUser currentUser = require(ReviewAppService.RESULT_AGGREGATE);
        return ApiResponse.success(
                reviewAppService.aggregateBatch(currentUser, batchId),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/batches/{batchId}/candidates/{candidateId}/decision")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Aggregate> decideCandidate(
            @PathVariable("batchId") Long batchId,
            @PathVariable("candidateId") Long candidateId,
            @Valid @RequestBody ReviewDTO.AggregateDecisionRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.RESULT_FINALIZE);
        return ApiResponse.success(
                reviewAppService.decideCandidate(currentUser, batchId, candidateId, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/finalize")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Batch> finalizeBatch(@PathVariable("batchId") Long batchId) {
        CurrentUser currentUser = require(ReviewAppService.RESULT_FINALIZE);
        return ApiResponse.success(
                reviewAppService.finalizeBatch(currentUser, batchId),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/publish")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Publication> publishBatch(@PathVariable("batchId") Long batchId) {
        CurrentUser currentUser = require(ReviewAppService.RESULT_PUBLISH);
        return ApiResponse.success(
                reviewAppService.publishBatch(currentUser, batchId),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/batches/{batchId}/correction")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Batch> reopenPublishedBatchForCorrection(
            @PathVariable("batchId") Long batchId,
            @Valid @RequestBody ReviewDTO.PublicationCorrectionRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.RESULT_PUBLISH);
        return ApiResponse.success(
                reviewAppService.reopenPublishedBatchForCorrection(
                        currentUser,
                        batchId,
                        request
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/results/mine")
    public ApiResponse<java.util.List<ReviewVO.PublishedResult>> myPublishedResults() {
        CurrentUser currentUser = require(ReviewAppService.APPEAL_SUBMIT);
        return ApiResponse.success(
                reviewAppService.listMyPublishedResults(currentUser),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/appeals/mine")
    public ApiResponse<java.util.List<ReviewVO.Appeal>> myAppeals() {
        CurrentUser currentUser = require(ReviewAppService.APPEAL_SUBMIT);
        return ApiResponse.success(
                reviewAppService.listMyAppeals(currentUser),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/publications/{publicationId}/registrations/{registrationId}/appeals")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Appeal> submitAppeal(
            @PathVariable("publicationId") Long publicationId,
            @PathVariable("registrationId") Long registrationId,
            @Valid @RequestBody ReviewDTO.AppealSubmitRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.APPEAL_SUBMIT);
        return ApiResponse.success(
                reviewAppService.submitAppeal(
                        currentUser,
                        publicationId,
                        registrationId,
                        request
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/appeals")
    public ApiResponse<java.util.List<ReviewVO.Appeal>> appeals(
            @RequestParam(value = "batchId", required = false) Long batchId,
            @RequestParam(value = "status", required = false) String status
    ) {
        CurrentUser currentUser = require(ReviewAppService.APPEAL_MANAGE);
        return ApiResponse.success(
                reviewAppService.listAppeals(currentUser, batchId, status),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/appeals/{appealId}/resolution")
    @RepeatSubmit
    public ApiResponse<ReviewVO.Appeal> resolveAppeal(
            @PathVariable("appealId") Long appealId,
            @Valid @RequestBody ReviewDTO.AppealResolveRequest request
    ) {
        CurrentUser currentUser = require(ReviewAppService.APPEAL_MANAGE);
        return ApiResponse.success(
                reviewAppService.resolveAppeal(currentUser, appealId, request),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser require(String permission) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (isTrustedCurrentUser(currentUser) && sessionAuthenticationService != null) {
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                    sessionAuthenticationService.authenticateSessionTicket(
                    currentUser.getSessionId(),
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    currentUser.getSimulatedRoleId(),
                    currentUser.getSessionVersion(),
                    currentUser.getPermissionsVersion()
            );
            currentUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        } else if (enforceTrustedSession) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted session resolver is unavailable");
        }
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        permissionGuard.requirePermission(currentUser, permission);
        return currentUser;
    }
}
