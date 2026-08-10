package com.lumira.saas.modules.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.PlatformEventPort;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.registration.api.RegistrationReviewInternalApi;
import com.lumira.saas.modules.review.dto.ReviewDTO;
import com.lumira.saas.modules.review.repository.ReviewRepository;
import com.lumira.saas.modules.review.vo.ReviewVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewAppServiceTest {

    private ReviewRepository repository;
    private ReviewAppService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReviewRepository.class);
        service = new ReviewAppService(repository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createsDraftPlanWithAnImmutableCriteriaVersionBoundary() {
        ReviewDTO.PlanCreateRequest request = planRequest();
        RegistrationReviewInternalApi registrationApi = mock(RegistrationReviewInternalApi.class);
        service.setRegistrationReviewInternalApi(registrationApi);
        when(registrationApi.stageBelongsToCompetition(10L, 20L)).thenReturn(true);
        when(repository.findPlanByStage(10L, 20L)).thenReturn(Optional.empty());
        when(repository.insertPlan(
                any(), anyString(), anyInt(), anyInt(), anyString(), any(), anyInt(), anyInt(), anyLong(), anyString()
        )).thenReturn(30L);
        when(repository.insertCriteriaVersion(
                anyLong(), anyInt(), anyString(), any(), anyLong(), anyString()
        )).thenReturn(40L);
        when(repository.insertCriterion(anyLong(), any(), anyInt(), anyLong(), anyString()))
                .thenReturn(50L, 51L);
        ReviewVO.Plan stored = plan(30L, "DRAFT", null);
        when(repository.findPlan(30L)).thenReturn(Optional.of(stored));
        when(repository.listCriteria(40L)).thenReturn(List.of());

        ReviewVO.Plan created = service.createPlan(user(ReviewAppService.PLAN_MANAGE), request);

        assertThat(created.getId()).isEqualTo(30L);
        verify(registrationApi).stageBelongsToCompetition(10L, 20L);
        verify(repository, never()).stageBelongsToCompetition(anyLong(), anyLong());
        verify(repository).insertCriteriaVersion(
                eq(30L),
                eq(1),
                eq("初评方案 v1"),
                eq(new BigDecimal("1.00")),
                eq(7L),
                eq("user-uuid")
        );
    }

    @Test
    void rejectsWeightsThatDoNotAddUpToOneBeforeWriting() {
        ReviewDTO.PlanCreateRequest request = planRequest();
        request.getCriteria().get(0).setWeight(new BigDecimal("0.40"));
        when(repository.stageBelongsToCompetition(10L, 20L)).thenReturn(true);
        when(repository.findPlanByStage(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPlan(user(ReviewAppService.PLAN_MANAGE), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("weights must add up to 1");

        verify(repository, never()).insertPlan(
                any(), anyString(), anyInt(), anyInt(), anyString(), any(), anyInt(), anyInt(), anyLong(), anyString()
        );
    }

    @Test
    void activatesPlanByPublishingCriteriaBeforeMarkingPlanReady() {
        ReviewVO.Plan draft = plan(30L, "DRAFT", null);
        when(repository.findPlan(30L)).thenReturn(Optional.of(draft));
        when(repository.findDraftCriteriaVersionId(30L)).thenReturn(40L);
        ReviewVO.Criterion criterion = new ReviewVO.Criterion();
        criterion.setId(50L);
        criterion.setCriterionCode("QUALITY");
        criterion.setCriterionName("质量");
        criterion.setWeight(BigDecimal.ONE);
        criterion.setMaximumScore(new BigDecimal("100"));
        criterion.setRequired(true);
        criterion.setSortOrder(10);
        when(repository.listCriteria(40L)).thenReturn(List.of(criterion));
        when(repository.publishCriteriaVersion(anyLong(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(1);
        when(repository.markPlanReady(anyLong(), anyLong(), anyInt(), anyLong(), anyString(), any()))
                .thenReturn(1);

        service.activatePlan(user(ReviewAppService.PLAN_MANAGE), 30L);

        verify(repository).publishCriteriaVersion(anyLong(), anyString(), anyLong(), anyString(), any());
        verify(repository).markPlanReady(anyLong(), anyLong(), anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void listsPlansWithTheirPublishedOrDraftCriteria() {
        ReviewVO.Plan draft = plan(30L, "DRAFT", null);
        ReviewVO.Plan ready = plan(31L, "READY", 41L);
        ReviewVO.Criterion draftCriterion = new ReviewVO.Criterion();
        draftCriterion.setId(50L);
        ReviewVO.Criterion readyCriterion = new ReviewVO.Criterion();
        readyCriterion.setId(51L);
        when(repository.listPlans(10L, 20L)).thenReturn(List.of(draft, ready));
        when(repository.findDraftCriteriaVersionId(30L)).thenReturn(40L);
        when(repository.listCriteria(40L)).thenReturn(List.of(draftCriterion));
        when(repository.listCriteria(41L)).thenReturn(List.of(readyCriterion));

        List<ReviewVO.Plan> result = service.listPlans(
                user(ReviewAppService.PLAN_MANAGE),
                10L,
                20L
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCriteria()).extracting(ReviewVO.Criterion::getId).containsExactly(50L);
        assertThat(result.get(1).getCriteria()).extracting(ReviewVO.Criterion::getId).containsExactly(51L);
    }

    @Test
    void rejectsInvalidOptionalPlanListFilterBeforeQuerying() {
        assertThatThrownBy(() -> service.listPlans(
                user(ReviewAppService.PLAN_MANAGE),
                0L,
                null
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("must be positive");

        verify(repository, never()).listPlans(any(), any());
    }

    @Test
    void createsBatchOnlyFromReadyPlanWithLockedCriteria() {
        ReviewVO.Plan ready = plan(30L, "READY", 40L);
        when(repository.findPlan(30L)).thenReturn(Optional.of(ready));
        when(repository.insertBatch(any(), anyString(), any(), anyString(), anyLong(), anyString()))
                .thenReturn(60L);
        ReviewVO.Batch batch = new ReviewVO.Batch();
        batch.setId(60L);
        batch.setPlanId(30L);
        batch.setStatus("DRAFT");
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        ReviewDTO.BatchCreateRequest request = new ReviewDTO.BatchCreateRequest();
        request.setPlanId(30L);
        request.setBatchName("第一批初评");

        ReviewVO.Batch created = service.createBatch(user(ReviewAppService.BATCH_CREATE), request);

        assertThat(created.getId()).isEqualTo(60L);
        assertThat(created.getStatus()).isEqualTo("DRAFT");
        assertThat(request.getReviewerCountPerCandidate()).isEqualTo(3);
        assertThat(request.getExpertMinAssignments()).isEqualTo(5);
        assertThat(request.getExpertTargetAssignments()).isEqualTo(6);
        assertThat(request.getExpertMaxAssignments()).isEqualTo(6);
    }

    @Test
    void refusesAssignmentConfirmationWhenWorkloadRangeCannotCoverAllTasks() {
        ReviewVO.Batch assigning = reviewBatch(60L, "ASSIGNING", 2);
        assigning.setReviewerCountPerCandidate(3);
        assigning.setExpertMinAssignments(5);
        assigning.setExpertTargetAssignments(6);
        assigning.setExpertMaxAssignments(6);
        when(repository.findBatch(60L)).thenReturn(Optional.of(assigning));
        when(repository.listSelectedRosterExpertIds(60L)).thenReturn(List.of(80L));
        ReviewVO.Candidate candidate = new ReviewVO.Candidate();
        candidate.setId(70L);
        when(repository.listCandidates(60L)).thenReturn(List.of(candidate));

        assertThatThrownBy(() -> service.confirmAssignments(
                user(ReviewAppService.ASSIGNMENT_MANAGE),
                60L
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("cannot fit");

        verify(repository, never()).markBatchAssignmentsConfirmed(
                anyLong(), anyInt(), anyLong(), anyString(), any()
        );
    }

    @Test
    void opensInvitationWithQrButBlocksTasksUntilAdministratorCheckIn() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ReviewRepository.InvitationContext context = new ReviewRepository.InvitationContext(
                900L,
                60L,
                "第一批初评",
                10L,
                80L,
                7L,
                "user-uuid",
                "评审专家",
                "expert@example.com",
                "SENT",
                "token-hash",
                now.plusHours(1),
                null,
                null,
                now,
                null
        );
        when(repository.findInvitationByTokenHash(anyString())).thenReturn(Optional.of(context));
        when(repository.issueInvitationQr(eq(900L), anyString(), any(), any())).thenReturn(1);

        ReviewVO.Invitation opened = service.openInvitation("raw-token");

        assertThat(opened.getStatus()).isEqualTo("QR_ISSUED");
        assertThat(opened.getQrValue()).isNotBlank();
        assertThat(opened.getQrExpiresAt()).isAfter(now);
        verify(repository).issueInvitationQr(eq(900L), anyString(), any(), any());

        assertThatThrownBy(() -> service.listInvitationAssignments("raw-token"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("check-in");
    }

    @Test
    void rejectsCheckInQrFromAnotherReviewBatchAndKeepsAnAuditTrail() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ReviewRepository.InvitationContext context = new ReviewRepository.InvitationContext(
                901L,
                61L,
                "第二批初评",
                11L,
                81L,
                7L,
                "user-uuid",
                "评审专家",
                "expert@example.com",
                "QR_ISSUED",
                "token-hash",
                now.plusHours(1),
                now.plusMinutes(4),
                null,
                now,
                null
        );
        when(repository.findInvitationByQrTokenHash(anyString())).thenReturn(Optional.of(context));
        ReviewDTO.CheckInRequest request = new ReviewDTO.CheckInRequest();
        request.setQrToken("qr-token");

        assertThatThrownBy(() -> service.checkIn(
                user(ReviewAppService.CHECKIN_SCAN),
                60L,
                request
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("another review batch");

        verify(repository).recordCheckinAttempt(
                eq(60L), eq(901L), eq(81L), anyString(), eq("REJECTED"),
                eq("二维码不属于当前评审批次"), eq(7L), eq("user-uuid"), any()
        );
    }

    @Test
    void freezesOnlyDatasetLinkedCandidatesIntoHashedSnapshots() {
        ReviewVO.Plan ready = plan(30L, "READY", 40L);
        ready.setBlindMode("DOUBLE_BLIND");
        ReviewVO.Batch draftBatch = new ReviewVO.Batch();
        draftBatch.setId(60L);
        draftBatch.setPlanId(30L);
        draftBatch.setCompetitionId(10L);
        draftBatch.setCriteriaVersionId(40L);
        draftBatch.setStatus("DRAFT");
        draftBatch.setVersion(1);
        ReviewVO.Batch readyBatch = new ReviewVO.Batch();
        readyBatch.setId(60L);
        readyBatch.setStatus("READY");
        readyBatch.setCandidateCount(1);
        when(repository.findBatch(60L)).thenReturn(Optional.of(draftBatch), Optional.of(readyBatch));
        when(repository.findPlan(30L)).thenReturn(Optional.of(ready));
        when(repository.loadCandidateSnapshots(10L, List.of(100L))).thenReturn(List.of(
                new ReviewRepository.CandidateSnapshot(
                        100L,
                        "REG-100",
                        10L,
                        200L,
                        300L,
                        7L,
                        "user-uuid",
                        "CONFIRMED",
                        "{\"answers\":{\"school\":\"Lumira University\"}}",
                        "{\"teamName\":\"Alpha\"}",
                        """
                        {
                          "title": "Project A",
                          "description": "A legitimate project summary",
                          "extraValues": {
                            "intellectualProperties": [
                              {
                                "intellectualPropertyName": "Lumira Platform",
                                "rightsHolder": "Student A",
                                "contactEmail": "student-a@example.com"
                              }
                            ],
                            "ownerUserUuid": "user-uuid"
                          }
                        }
                        """,
                        "[{\"name\":\"Student A\"}]",
                        """
                        [
                          {
                            "scope": "PROJECT_FIELD",
                            "itemKey": "rightsHolder",
                            "title": "权利人",
                            "fieldType": "TEXT"
                          },
                          {
                            "scope": "PROJECT_FIELD",
                            "itemKey": "projectTrack",
                            "title": "项目赛道",
                            "fieldType": "SELECT"
                          }
                        ]
                        """,
                        """
                        [
                          {
                            "fieldKey": "work",
                            "fileId": 9001,
                            "originalFileName": "Student A-project.pdf",
                            "jsonValue": {
                              "memberName": "Student A",
                              "mobile": "13800138000",
                              "summary": "Public project evidence"
                            }
                          }
                        ]
                        """
                )
        ));
        when(repository.insertCandidate(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString()
        )).thenReturn(70L);
        when(repository.markBatchReady(
                anyLong(), anyString(), anyInt(), anyInt(), anyLong(), anyString(), any()
        )).thenReturn(1);
        ReviewDTO.BatchFreezeRequest request = new ReviewDTO.BatchFreezeRequest();
        request.setRegistrationIds(List.of(100L));

        ReviewVO.Batch frozen = service.freezeBatch(user(ReviewAppService.BATCH_CREATE), 60L, request);

        assertThat(frozen.getStatus()).isEqualTo("READY");
        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reviewSnapshotCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).insertCandidate(
                eq(60L),
                eq(100L),
                eq("C00001"),
                snapshotCaptor.capture(),
                reviewSnapshotCaptor.capture(),
                hashCaptor.capture(),
                eq(7L),
                eq("user-uuid")
        );
        assertThat(snapshotCaptor.getValue())
                .contains("\"registrationNo\":\"REG-100\"")
                .contains("\"fileId\":9001")
                .contains("\"members\"");
        assertThat(reviewSnapshotCaptor.getValue())
                .contains("\"schemaVersion\":2")
                .contains("\"blindCode\":\"C00001\"")
                .contains("\"fileId\":9001")
                .contains("\"title\":\"Project A\"")
                .contains("\"summary\":\"Public project evidence\"")
                .doesNotContain("registrationNo")
                .doesNotContain("ownerUserUuid")
                .doesNotContain("\"members\"")
                .doesNotContain("rightsHolder")
                .doesNotContain("Student A")
                .doesNotContain("student-a@example.com")
                .doesNotContain("13800138000")
                .doesNotContain("originalFileName");
        assertThat(hashCaptor.getValue()).matches("[0-9a-f]{64}");
    }

    @Test
    void listsFrozenCandidatesOnlyAfterVerifyingBatchAndPermission() {
        ReviewVO.Batch batch = reviewBatch(60L, "READY", 1);
        ReviewVO.Candidate candidate = new ReviewVO.Candidate();
        candidate.setId(70L);
        candidate.setBatchId(60L);
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.listCandidates(60L)).thenReturn(List.of(candidate));

        List<ReviewVO.Candidate> result = service.listCandidates(
                user(ReviewAppService.ASSIGNMENT_MANAGE),
                60L
        );

        assertThat(result).extracting(ReviewVO.Candidate::getId).containsExactly(70L);
    }

    @Test
    void assignsOnlyApprovedExpertsAndMovesBatchIntoAssigning() {
        ReviewVO.Batch readyBatch = reviewBatch(60L, "READY", 1);
        ReviewVO.Batch assigningBatch = reviewBatch(60L, "ASSIGNING", 2);
        when(repository.findBatch(60L)).thenReturn(Optional.of(readyBatch), Optional.of(assigningBatch));
        ReviewRepository.AssignmentTarget target = assignmentTarget(false);
        when(repository.findAssignmentTarget(60L, 70L, 80L)).thenReturn(Optional.of(target));
        when(repository.assignmentExists(70L, 80L)).thenReturn(false);
        when(repository.insertAssignment(
                eq(60L), eq(target), eq(BigDecimal.ONE), any(), eq(7L), eq("user-uuid")
        ))
                .thenReturn(90L);
        when(repository.markBatchAssigning(eq(60L), eq(1), eq(7L), eq("user-uuid"), any()))
                .thenReturn(1);
        when(repository.countCandidatesBelowMinimumAssignments(60L, 3)).thenReturn(1);
        ReviewDTO.AssignmentCreateRequest request = assignmentRequest(70L, 80L);

        ReviewVO.AssignmentResult result = service.assignExperts(
                user(ReviewAppService.ASSIGNMENT_MANAGE),
                60L,
                request
        );

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getBatchStatus()).isEqualTo("ASSIGNING");
        assertThat(result.getCandidatesBelowMinimum()).isEqualTo(1);
    }

    @Test
    void rejectsExpertIdentityConflictBeforeCreatingAssignment() {
        when(repository.findBatch(60L)).thenReturn(Optional.of(reviewBatch(60L, "READY", 1)));
        when(repository.findAssignmentTarget(60L, 70L, 80L))
                .thenReturn(Optional.of(assignmentTarget(true)));

        assertThatThrownBy(() -> service.assignExperts(
                user(ReviewAppService.ASSIGNMENT_MANAGE),
                60L,
                assignmentRequest(70L, 80L)
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("identity conflict");

        verify(repository, never()).insertAssignment(
                anyLong(), any(), any(), any(), anyLong(), anyString()
        );
    }

    @Test
    void autoAssignmentBalancesWorkloadAndFillsTheRequiredReviewerCount() {
        ReviewVO.Batch readyBatch = reviewBatch(60L, "READY", 1);
        readyBatch.setMinimumReviewerCount(2);
        ReviewVO.Batch assigningBatch = reviewBatch(60L, "ASSIGNING", 2);
        assigningBatch.setMinimumReviewerCount(2);
        when(repository.findBatch(60L)).thenReturn(
                Optional.of(readyBatch),
                Optional.of(readyBatch),
                Optional.of(assigningBatch)
        );
        ReviewVO.Candidate candidate = new ReviewVO.Candidate();
        candidate.setId(70L);
        candidate.setBatchId(60L);
        when(repository.listCandidates(60L)).thenReturn(List.of(candidate));
        when(repository.listAssignments(60L)).thenReturn(List.of());
        when(repository.listApprovedExpertWorkloads()).thenReturn(List.of(
                new ReviewRepository.ExpertWorkload(80L, 4),
                new ReviewRepository.ExpertWorkload(81L, 0)
        ));
        ReviewRepository.AssignmentTarget target80 = assignmentTarget(70L, 80L, false);
        ReviewRepository.AssignmentTarget target81 = assignmentTarget(70L, 81L, false);
        when(repository.findAssignmentTarget(60L, 70L, 80L)).thenReturn(Optional.of(target80));
        when(repository.findAssignmentTarget(60L, 70L, 81L)).thenReturn(Optional.of(target81));
        when(repository.assignmentExists(anyLong(), anyLong())).thenReturn(false);
        when(repository.insertAssignment(
                anyLong(), any(), eq(BigDecimal.ONE), any(), anyLong(), anyString()
        )).thenReturn(90L, 91L);
        when(repository.markBatchAssigning(eq(60L), eq(1), eq(7L), eq("user-uuid"), any()))
                .thenReturn(1);
        when(repository.countCandidatesBelowMinimumAssignments(60L, 2)).thenReturn(0);

        ReviewVO.AssignmentResult result = service.autoAssignExperts(
                user(ReviewAppService.ASSIGNMENT_MANAGE),
                60L,
                new ReviewDTO.AutoAssignmentRequest()
        );

        assertThat(result.getCreatedCount()).isEqualTo(2);
        ArgumentCaptor<ReviewRepository.AssignmentTarget> targetCaptor =
                ArgumentCaptor.forClass(ReviewRepository.AssignmentTarget.class);
        verify(repository, org.mockito.Mockito.times(2)).insertAssignment(
                eq(60L),
                targetCaptor.capture(),
                eq(BigDecimal.ONE),
                any(),
                eq(7L),
                eq("user-uuid")
        );
        assertThat(targetCaptor.getAllValues())
                .extracting(ReviewRepository.AssignmentTarget::expertId)
                .containsExactly(81L, 80L);
    }

    @Test
    void refusesToStartReviewUntilEveryCandidateHasEnoughExperts() {
        ReviewVO.Batch assigning = reviewBatch(60L, "ASSIGNING", 2);
        when(repository.findBatch(60L)).thenReturn(Optional.of(assigning));
        when(repository.countCandidatesBelowMinimumAssignments(60L, 3)).thenReturn(2);

        assertThatThrownBy(() ->
                service.startReview(user(ReviewAppService.ASSIGNMENT_MANAGE), 60L)
        ).isInstanceOf(BizException.class)
                .hasMessageContaining("2 candidates");

        verify(repository, never()).markBatchInReview(
                anyLong(), anyInt(), anyLong(), anyString(), any()
        );
    }

    @Test
    void startsReviewWhenEveryCandidateHasEnoughExperts() {
        ReviewVO.Batch assigning = reviewBatch(60L, "ASSIGNING", 2);
        ReviewVO.Batch inReview = reviewBatch(60L, "IN_REVIEW", 3);
        when(repository.findBatch(60L)).thenReturn(Optional.of(assigning), Optional.of(inReview));
        when(repository.countCandidatesBelowMinimumAssignments(60L, 3)).thenReturn(0);
        when(repository.markBatchInReview(eq(60L), eq(2), eq(7L), eq("user-uuid"), any()))
                .thenReturn(1);

        ReviewVO.Batch started = service.startReview(user(ReviewAppService.ASSIGNMENT_MANAGE), 60L);

        assertThat(started.getStatus()).isEqualTo("IN_REVIEW");
    }

    @Test
    void revokesActiveAssignmentWithReasonBeforeAggregation() {
        ReviewVO.Batch batch = reviewBatch(60L, "IN_REVIEW", 3);
        ReviewVO.AdminAssignment active = new ReviewVO.AdminAssignment();
        active.setId(90L);
        active.setBatchId(60L);
        active.setStatus("IN_PROGRESS");
        ReviewVO.AdminAssignment revoked = new ReviewVO.AdminAssignment();
        revoked.setId(90L);
        revoked.setBatchId(60L);
        revoked.setStatus("REVOKED");
        revoked.setRevokeReason("专家临时退出");
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.listAssignments(60L))
                .thenReturn(List.of(active), List.of(revoked));
        when(repository.revokeAssignment(
                eq(60L), eq(90L), eq("专家临时退出"), eq(7L), eq("user-uuid"), any()
        )).thenReturn(1);
        ReviewDTO.AssignmentRevokeRequest request = new ReviewDTO.AssignmentRevokeRequest();
        request.setReason("专家临时退出");

        ReviewVO.AdminAssignment result = service.revokeAssignment(
                user(ReviewAppService.ASSIGNMENT_MANAGE),
                60L,
                90L,
                request
        );

        assertThat(result.getStatus()).isEqualTo("REVOKED");
        assertThat(result.getRevokeReason()).isEqualTo("专家临时退出");
    }

    @Test
    void expiresDueAssignmentsThroughInternalLifecycleEntryPoint() {
        when(repository.expireDueAssignments(any())).thenReturn(4);

        int expired = service.expireDueAssignments();

        assertThat(expired).isEqualTo(4);
        verify(repository).expireDueAssignments(any());
    }

    @Test
    void submitsImmutableSheetUsingLockedCriteriaAndCalculatedScale() {
        ReviewRepository.AssignmentContext assignment = scoringAssignment();
        when(repository.findOwnedAssignment(90L, 7L, "user-uuid")).thenReturn(Optional.of(assignment));
        when(repository.findLatestSheetVersion(90L)).thenReturn(1);
        when(repository.insertSheet(
                eq(assignment), eq(2), eq("SUBMITTED"), eq(new BigDecimal("68.0000")),
                eq("Well prepared"), any(), eq(7L), eq("user-uuid")
        )).thenReturn(101L);
        when(repository.insertScoreItem(eq(101L), any(), eq(7L), eq("user-uuid")))
                .thenReturn(201L, 202L);
        when(repository.markAssignmentSubmitted(eq(90L), eq(3), eq(7L), eq("user-uuid"), any()))
                .thenReturn(1);
        ReviewVO.ReviewSheet stored = new ReviewVO.ReviewSheet();
        stored.setId(101L);
        stored.setAssignmentId(90L);
        stored.setStatus("SUBMITTED");
        stored.setTotalScore(new BigDecimal("68.0000"));
        when(repository.findSheet(101L, 90L)).thenReturn(Optional.of(stored));
        when(repository.listScoreItems(101L)).thenReturn(List.of());

        ReviewVO.ReviewSheet submitted = service.submitSheet(
                user(ReviewAppService.SCORE_SUBMIT),
                90L,
                sheetRequest(score(501L, "80"), score(502L, "50"))
        );

        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");
        assertThat(submitted.getTotalScore()).isEqualByComparingTo("68.0000");
    }

    @Test
    void rejectsScoreOutsideLockedCriteriaBeforeWriting() {
        when(repository.findOwnedAssignment(90L, 7L, "user-uuid"))
                .thenReturn(Optional.of(scoringAssignment()));

        assertThatThrownBy(() -> service.submitSheet(
                user(ReviewAppService.SCORE_SUBMIT),
                90L,
                sheetRequest(score(999L, "80"), score(502L, "50"))
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("locked criteria");

        verify(repository, never()).insertSheet(
                any(), anyInt(), anyString(), any(), any(), any(), anyLong(), anyString()
        );
    }

    @Test
    void aggregatesSubmittedScoresWithConfiguredTrimmingAndStableRanks() {
        ReviewVO.Batch batch = reviewBatch(60L, "IN_REVIEW", 3);
        batch.setCandidateCount(2);
        ReviewVO.Plan plan = plan(30L, "READY", 40L);
        plan.setAggregateMethod("TRIMMED_MEAN");
        plan.setScoreScale(new BigDecimal("100"));
        plan.setTrimLowestCount(1);
        plan.setTrimHighestCount(1);
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.findPlan(30L)).thenReturn(Optional.of(plan));
        when(repository.countCandidatesBelowMinimumSubmitted(60L, 2)).thenReturn(0);
        when(repository.markBatchAggregating(eq(60L), eq(3), eq(7L), eq("user-uuid"), any()))
                .thenReturn(1);
        when(repository.loadAggregationSources(60L)).thenReturn(List.of(
                new ReviewRepository.AggregationSource(
                        70L,
                        List.of(
                                weightedScore("10", "1"),
                                weightedScore("80", "1"),
                                weightedScore("90", "1"),
                                weightedScore("100", "1")
                        )
                ),
                new ReviewRepository.AggregationSource(
                        71L,
                        List.of(
                                weightedScore("60", "1"),
                                weightedScore("70", "1"),
                                weightedScore("80", "1")
                        )
                )
        ));
        when(repository.upsertAggregate(
                anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(),
                anyString(), anyLong(), anyString(), any()
        )).thenReturn(1);
        when(repository.listAggregates(60L)).thenReturn(List.of());

        service.aggregateBatch(user(ReviewAppService.RESULT_AGGREGATE), 60L);

        verify(repository).upsertAggregate(
                eq(60L),
                eq(70L),
                eq(new BigDecimal("85.0000")),
                eq(new BigDecimal("10")),
                eq(new BigDecimal("100")),
                any(),
                eq(4),
                eq(2),
                eq(1),
                eq("[\"HIGH_SCORE_SPREAD\",\"TRIMMED_SCORES\"]"),
                eq(7L),
                eq("user-uuid"),
                any()
        );
        verify(repository).upsertAggregate(
                eq(60L),
                eq(71L),
                eq(new BigDecimal("70.0000")),
                eq(new BigDecimal("60")),
                eq(new BigDecimal("80")),
                any(),
                eq(3),
                eq(1),
                eq(2),
                eq("[\"TRIMMED_SCORES\"]"),
                eq(7L),
                eq("user-uuid"),
                any()
        );
    }

    @Test
    void aggregatesWithMedianWithoutOverwritingSourceScores() {
        ReviewVO.Batch batch = reviewBatch(60L, "IN_REVIEW", 3);
        batch.setCandidateCount(1);
        ReviewVO.Plan plan = plan(30L, "READY", 40L);
        plan.setAggregateMethod("MEDIAN");
        plan.setScoreScale(new BigDecimal("100"));
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.findPlan(30L)).thenReturn(Optional.of(plan));
        when(repository.countCandidatesBelowMinimumSubmitted(60L, 2)).thenReturn(0);
        when(repository.markBatchAggregating(eq(60L), eq(3), eq(7L), eq("user-uuid"), any()))
                .thenReturn(1);
        when(repository.loadAggregationSources(60L)).thenReturn(List.of(
                new ReviewRepository.AggregationSource(
                        70L,
                        List.of(
                                weightedScore("10", "1"),
                                weightedScore("50", "1"),
                                weightedScore("90", "1")
                        )
                )
        ));
        when(repository.upsertAggregate(
                anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(),
                anyString(), anyLong(), anyString(), any()
        )).thenReturn(1);
        when(repository.listAggregates(60L)).thenReturn(List.of());

        service.aggregateBatch(user(ReviewAppService.RESULT_AGGREGATE), 60L);

        verify(repository).upsertAggregate(
                eq(60L), eq(70L), eq(new BigDecimal("50.0000")),
                any(), any(), any(), eq(3), eq(3), eq(1),
                anyString(), eq(7L), eq("user-uuid"), any()
        );
    }

    @Test
    void aggregatesWithReviewerWeightsWhenConfigured() {
        ReviewVO.Batch batch = reviewBatch(60L, "IN_REVIEW", 3);
        batch.setCandidateCount(1);
        ReviewVO.Plan plan = plan(30L, "READY", 40L);
        plan.setAggregateMethod("WEIGHTED_AVERAGE");
        plan.setScoreScale(new BigDecimal("100"));
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.findPlan(30L)).thenReturn(Optional.of(plan));
        when(repository.countCandidatesBelowMinimumSubmitted(60L, 2)).thenReturn(0);
        when(repository.markBatchAggregating(eq(60L), eq(3), eq(7L), eq("user-uuid"), any()))
                .thenReturn(1);
        when(repository.loadAggregationSources(60L)).thenReturn(List.of(
                new ReviewRepository.AggregationSource(
                        70L,
                        List.of(weightedScore("50", "1"), weightedScore("100", "3"))
                )
        ));
        when(repository.upsertAggregate(
                anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(),
                anyString(), anyLong(), anyString(), any()
        )).thenReturn(1);
        when(repository.listAggregates(60L)).thenReturn(List.of());

        service.aggregateBatch(user(ReviewAppService.RESULT_AGGREGATE), 60L);

        verify(repository).upsertAggregate(
                eq(60L), eq(70L), eq(new BigDecimal("87.5000")),
                any(), any(), any(), eq(2), eq(2), eq(1),
                anyString(), eq(7L), eq("user-uuid"), any()
        );
    }

    @Test
    void recordsAnomalousCandidateDecisionAsAuditedArbitration() {
        ReviewVO.Batch batch = reviewBatch(60L, "AGGREGATING", 4);
        ReviewVO.Aggregate current = new ReviewVO.Aggregate();
        current.setBatchId(60L);
        current.setCandidateId(70L);
        current.setDecision("PENDING");
        current.setAnomalyFlagsJson("[\"HIGH_SCORE_SPREAD\"]");
        ReviewVO.Aggregate saved = new ReviewVO.Aggregate();
        saved.setBatchId(60L);
        saved.setCandidateId(70L);
        saved.setDecision("ADVANCED");
        saved.setDecisionReason("边界同分，经评审委员会复核");
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.listAggregates(60L)).thenReturn(List.of(current), List.of(saved));
        when(repository.updateAggregateDecision(
                eq(60L),
                eq(70L),
                eq("ADVANCED"),
                eq("边界同分，经评审委员会复核"),
                eq(7L),
                eq("user-uuid"),
                any()
        )).thenReturn(1);
        ReviewDTO.AggregateDecisionRequest request = new ReviewDTO.AggregateDecisionRequest();
        request.setDecision("ADVANCED");
        request.setReason("边界同分，经评审委员会复核");

        ReviewVO.Aggregate result = service.decideCandidate(
                user(ReviewAppService.RESULT_FINALIZE),
                60L,
                70L,
                request
        );

        assertThat(result.getDecision()).isEqualTo("ADVANCED");
        assertThat(result.getDecisionReason()).contains("评审委员会");
    }

    @Test
    void requiresArbitrationReasonForAnomalousCandidate() {
        ReviewVO.Batch batch = reviewBatch(60L, "AGGREGATING", 4);
        ReviewVO.Aggregate current = new ReviewVO.Aggregate();
        current.setBatchId(60L);
        current.setCandidateId(70L);
        current.setAnomalyFlagsJson("[\"HIGH_SCORE_STDDEV\"]");
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.listAggregates(60L)).thenReturn(List.of(current));
        ReviewDTO.AggregateDecisionRequest request = new ReviewDTO.AggregateDecisionRequest();
        request.setDecision("PASS");

        assertThatThrownBy(() -> service.decideCandidate(
                user(ReviewAppService.RESULT_FINALIZE),
                60L,
                70L,
                request
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("arbitration reason");

        verify(repository, never()).updateAggregateDecision(
                anyLong(), anyLong(), anyString(), any(), anyLong(), anyString(), any()
        );
    }

    @Test
    void refusesToFinalizeWhileAnyDecisionIsPending() {
        ReviewVO.Batch batch = reviewBatch(60L, "AGGREGATING", 4);
        batch.setCandidateCount(1);
        ReviewVO.Aggregate aggregate = new ReviewVO.Aggregate();
        aggregate.setCandidateId(70L);
        aggregate.setDecision("PENDING");
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.listAggregates(60L)).thenReturn(List.of(aggregate));
        when(repository.countPendingAggregateDecisions(60L)).thenReturn(1);

        assertThatThrownBy(() -> service.finalizeBatch(
                user(ReviewAppService.RESULT_FINALIZE),
                60L
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("1 review decisions");

        verify(repository, never()).finalizeAggregates(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void returnsExistingPublicationForIdempotentPublishRetry() {
        ReviewVO.Batch batch = reviewBatch(60L, "PUBLISHED", 6);
        ReviewVO.Publication publication = new ReviewVO.Publication();
        publication.setId(500L);
        publication.setBatchId(60L);
        publication.setPublicationVersion(1);
        publication.setStatus("PUBLISHED");
        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.findLatestPublication(60L)).thenReturn(Optional.of(publication));

        ReviewVO.Publication result = service.publishBatch(
                user(ReviewAppService.RESULT_PUBLISH),
                60L
        );

        assertThat(result.getId()).isEqualTo(500L);
        verify(repository, never()).insertPublication(
                anyLong(), anyInt(), anyString(), anyString(), anyLong(), anyString(), any()
        );
    }

    @Test
    void publishesOneIdentityBoundEventPerTeamResult() {
        ReviewVO.Batch batch = reviewBatch(60L, "FINALIZED", 5);
        batch.setStageId(20L);
        ReviewRepository.PublicationRow row = new ReviewRepository.PublicationRow(
                70L,
                100L,
                11L,
                "owner-uuid",
                "BLIND-001",
                new BigDecimal("91.2500"),
                1,
                "ADVANCED"
        );
        ReviewVO.Publication publication = new ReviewVO.Publication();
        publication.setId(500L);
        publication.setBatchId(60L);
        publication.setPublicationVersion(1);
        publication.setStatus("PUBLISHED");
        PlatformEventPort eventPublisher = mock(PlatformEventPort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        service.setPlatformEventPublisher(eventPublisher);
        service.setMeterRegistry(meterRegistry);

        when(repository.findBatch(60L)).thenReturn(Optional.of(batch));
        when(repository.loadPublicationRows(60L)).thenReturn(List.of(row));
        when(repository.findLatestPublicationVersion(60L)).thenReturn(0);
        when(repository.insertPublication(
                eq(60L), eq(1), anyString(), anyString(), eq(7L), eq("user-uuid"), any()
        )).thenReturn(500L);
        when(repository.projectLegacyResult(
                eq(batch), eq(row), eq(7L), eq("user-uuid"), any()
        )).thenReturn(1);
        when(repository.markBatchPublished(
                eq(60L), eq(5), eq(7L), eq("user-uuid"), any()
        )).thenReturn(1);
        when(repository.findLatestPublication(60L)).thenReturn(Optional.of(publication));

        ReviewVO.Publication result = service.publishBatch(
                user(ReviewAppService.RESULT_PUBLISH),
                60L
        );

        assertThat(result.getId()).isEqualTo(500L);
        assertThat(meterRegistry.counter("competition.review.publication").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("competition.review.result.published").count()).isEqualTo(1);
        verify(eventPublisher, times(2)).record(
                eq("SYSTEM"),
                anyString(),
                eq(7L),
                anyString(),
                anyLong(),
                any()
        );
        verify(eventPublisher).record(
                eq("SYSTEM"),
                eq(ReviewAppService.RESULT_PUBLISHED_EVENT),
                eq(7L),
                eq("competition.review-result.v1"),
                eq(100L),
                argThat(attributes ->
                        attributes != null
                                && Long.valueOf(500L).equals(attributes.get("publicationId"))
                                && Integer.valueOf(1).equals(attributes.get("publicationVersion"))
                                && Long.valueOf(11L).equals(attributes.get("recipientUserId"))
                                && "owner-uuid".equals(attributes.get("recipientUserUuid"))
                                && "ADVANCED".equals(attributes.get("decision"))
                                && Integer.valueOf(1).equals(attributes.get("rankNo"))
                )
        );
    }

    @Test
    void reopensPublishedBatchAsAuditedCorrectionWithoutOverwritingPublicationSnapshot() {
        ReviewVO.Batch published = reviewBatch(60L, "PUBLISHED", 6);
        published.setCandidateCount(2);
        ReviewVO.Batch aggregating = reviewBatch(60L, "AGGREGATING", 7);
        aggregating.setCandidateCount(2);
        when(repository.findBatch(60L))
                .thenReturn(Optional.of(published), Optional.of(aggregating));
        when(repository.revokeLatestPublication(
                eq(60L), eq("申诉成立，需要更正名单"), eq(7L), eq("user-uuid"), any()
        )).thenReturn(1);
        when(repository.reopenFinalizedAggregates(
                eq(60L), eq(7L), eq("user-uuid"), any()
        )).thenReturn(2);
        when(repository.markLegacyProjectionCorrectionPending(
                eq(published), eq("申诉成立，需要更正名单"), eq(7L), eq("user-uuid"), any()
        )).thenReturn(2);
        when(repository.reopenPublishedBatch(
                eq(60L), eq(6), eq(7L), eq("user-uuid"), any()
        )).thenReturn(1);
        ReviewDTO.PublicationCorrectionRequest request =
                new ReviewDTO.PublicationCorrectionRequest();
        request.setReason("申诉成立，需要更正名单");

        ReviewVO.Batch result = service.reopenPublishedBatchForCorrection(
                user(ReviewAppService.RESULT_PUBLISH),
                60L,
                request
        );

        assertThat(result.getStatus()).isEqualTo("AGGREGATING");
        verify(repository).revokeLatestPublication(
                eq(60L), anyString(), eq(7L), eq("user-uuid"), any()
        );
        verify(repository, never()).insertPublication(
                anyLong(), anyInt(), anyString(), anyString(), anyLong(), anyString(), any()
        );
    }

    @Test
    void submitsAppealOnlyForPublishedResultOwnedByStableUserIdentity() {
        ReviewRepository.AppealTarget target = new ReviewRepository.AppealTarget(
                500L,
                1,
                60L,
                10L,
                20L,
                70L,
                100L,
                new BigDecimal("88.5000"),
                2,
                "WAITLIST",
                java.time.LocalDateTime.now()
        );
        when(repository.findOwnedAppealTarget(500L, 100L, 7L, "user-uuid"))
                .thenReturn(Optional.of(target));
        when(repository.findAppealByPublicationAndRegistration(500L, 100L))
                .thenReturn(Optional.empty());
        when(repository.insertAppeal(
                eq(target), anyString(), eq("评分材料存在遗漏"), eq(7L), eq("user-uuid")
        )).thenReturn(700L);
        ReviewVO.Appeal stored = appeal(700L, "SUBMITTED");
        when(repository.findAppeal(700L)).thenReturn(Optional.of(stored));
        ReviewDTO.AppealSubmitRequest request = new ReviewDTO.AppealSubmitRequest();
        request.setReason("评分材料存在遗漏");

        ReviewVO.Appeal result = service.submitAppeal(
                user(ReviewAppService.APPEAL_SUBMIT),
                500L,
                100L,
                request
        );

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        verify(repository).findOwnedAppealTarget(500L, 100L, 7L, "user-uuid");
    }

    @Test
    void appealRetryReturnsExistingRecordWithoutCreatingDuplicate() {
        ReviewRepository.AppealTarget target = new ReviewRepository.AppealTarget(
                500L, 1, 60L, 10L, 20L, 70L, 100L,
                new BigDecimal("88.5000"), 2, "WAITLIST", java.time.LocalDateTime.now()
        );
        ReviewVO.Appeal existing = appeal(700L, "SUBMITTED");
        when(repository.findOwnedAppealTarget(500L, 100L, 7L, "user-uuid"))
                .thenReturn(Optional.of(target));
        when(repository.findAppealByPublicationAndRegistration(500L, 100L))
                .thenReturn(Optional.of(existing));
        ReviewDTO.AppealSubmitRequest request = new ReviewDTO.AppealSubmitRequest();
        request.setReason("retry");

        ReviewVO.Appeal result = service.submitAppeal(
                user(ReviewAppService.APPEAL_SUBMIT),
                500L,
                100L,
                request
        );

        assertThat(result.getId()).isEqualTo(700L);
        verify(repository, never()).insertAppeal(
                any(), anyString(), anyString(), anyLong(), anyString()
        );
    }

    @Test
    void resolvesAppealWithAuditedDecisionWithoutChangingReviewScores() {
        ReviewVO.Appeal submitted = appeal(700L, "SUBMITTED");
        ReviewVO.Appeal resolved = appeal(700L, "ACCEPTED");
        resolved.setResolution("材料版本确有遗漏，进入更正发布流程");
        when(repository.findAppeal(700L)).thenReturn(Optional.of(submitted), Optional.of(resolved));
        when(repository.resolveAppeal(
                eq(700L),
                eq("ACCEPTED"),
                eq("材料版本确有遗漏，进入更正发布流程"),
                eq(7L),
                eq("user-uuid"),
                any()
        )).thenReturn(1);
        ReviewDTO.AppealResolveRequest request = new ReviewDTO.AppealResolveRequest();
        request.setDecision("ACCEPTED");
        request.setResolution("材料版本确有遗漏，进入更正发布流程");

        ReviewVO.Appeal result = service.resolveAppeal(
                user(ReviewAppService.APPEAL_MANAGE),
                700L,
                request
        );

        assertThat(result.getStatus()).isEqualTo("ACCEPTED");
        assertThat(result.getResolution()).contains("更正发布");
        verify(repository).resolveAppeal(
                eq(700L), eq("ACCEPTED"), anyString(), eq(7L), eq("user-uuid"), any()
        );
        verify(repository, never()).updateAggregateDecision(
                anyLong(), anyLong(), anyString(), any(), anyLong(), anyString(), any()
        );
    }

    private ReviewDTO.PlanCreateRequest planRequest() {
        ReviewDTO.PlanCreateRequest request = new ReviewDTO.PlanCreateRequest();
        request.setCompetitionId(10L);
        request.setStageId(20L);
        request.setPlanName("初评方案");
        request.setRequiredReviewerCount(3);
        request.setMinimumSubmittedCount(2);
        request.setCriteria(List.of(
                criterion("QUALITY", "质量", "0.60", "100"),
                criterion("INNOVATION", "创新", "0.40", "100")
        ));
        return request;
    }

    private ReviewDTO.CriterionRequest criterion(String code, String name, String weight, String max) {
        ReviewDTO.CriterionRequest criterion = new ReviewDTO.CriterionRequest();
        criterion.setCode(code);
        criterion.setName(name);
        criterion.setWeight(new BigDecimal(weight));
        criterion.setMaximumScore(new BigDecimal(max));
        return criterion;
    }

    private ReviewVO.Plan plan(Long id, String status, Long criteriaVersionId) {
        ReviewVO.Plan plan = new ReviewVO.Plan();
        plan.setId(id);
        plan.setCompetitionId(10L);
        plan.setStageId(20L);
        plan.setPlanName("初评方案");
        plan.setStatus(status);
        plan.setCriteriaVersionId(criteriaVersionId);
        plan.setMinimumSubmittedCount(2);
        plan.setVersion(1);
        return plan;
    }

    private ReviewVO.Batch reviewBatch(Long id, String status, int version) {
        ReviewVO.Batch batch = new ReviewVO.Batch();
        batch.setId(id);
        batch.setPlanId(30L);
        batch.setCompetitionId(10L);
        batch.setCriteriaVersionId(40L);
        batch.setStatus(status);
        batch.setVersion(version);
        batch.setCandidateCount(1);
        batch.setMinimumReviewerCount(3);
        return batch;
    }

    private ReviewRepository.AssignmentTarget assignmentTarget(boolean conflict) {
        return assignmentTarget(70L, 80L, conflict);
    }

    private ReviewRepository.AssignmentTarget assignmentTarget(Long candidateId, Long expertId, boolean conflict) {
        return new ReviewRepository.AssignmentTarget(
                candidateId,
                100L,
                "FROZEN",
                11L,
                "candidate-owner",
                expertId,
                12L,
                "expert-user",
                "active",
                "APPROVED",
                "ENABLED",
                conflict
        );
    }

    private ReviewDTO.AssignmentCreateRequest assignmentRequest(Long candidateId, Long expertId) {
        ReviewDTO.AssignmentItemRequest item = new ReviewDTO.AssignmentItemRequest();
        item.setCandidateId(candidateId);
        item.setExpertId(expertId);
        ReviewDTO.AssignmentCreateRequest request = new ReviewDTO.AssignmentCreateRequest();
        request.setAssignments(List.of(item));
        return request;
    }

    private ReviewRepository.AssignmentContext scoringAssignment() {
        ReviewVO.Criterion quality = new ReviewVO.Criterion();
        quality.setId(501L);
        quality.setCriterionCode("QUALITY");
        quality.setWeight(new BigDecimal("0.60"));
        quality.setMaximumScore(new BigDecimal("100"));
        quality.setRequired(true);
        ReviewVO.Criterion innovation = new ReviewVO.Criterion();
        innovation.setId(502L);
        innovation.setCriterionCode("INNOVATION");
        innovation.setWeight(new BigDecimal("0.40"));
        innovation.setMaximumScore(new BigDecimal("100"));
        innovation.setRequired(true);
        return new ReviewRepository.AssignmentContext(
                90L,
                60L,
                "IN_REVIEW",
                70L,
                80L,
                "IN_PROGRESS",
                3,
                40L,
                new BigDecimal("100"),
                null,
                List.of(quality, innovation)
        );
    }

    private ReviewDTO.ReviewSheetRequest sheetRequest(ReviewDTO.ScoreItemRequest... scores) {
        ReviewDTO.ReviewSheetRequest request = new ReviewDTO.ReviewSheetRequest();
        request.setReviewComment("Well prepared");
        request.setScores(List.of(scores));
        return request;
    }

    private ReviewDTO.ScoreItemRequest score(Long criterionId, String value) {
        ReviewDTO.ScoreItemRequest score = new ReviewDTO.ScoreItemRequest();
        score.setCriterionId(criterionId);
        score.setScore(new BigDecimal(value));
        return score;
    }

    private ReviewRepository.WeightedScore weightedScore(String score, String weight) {
        return new ReviewRepository.WeightedScore(new BigDecimal(score), new BigDecimal(weight));
    }

    private ReviewVO.Appeal appeal(Long id, String status) {
        ReviewVO.Appeal appeal = new ReviewVO.Appeal();
        appeal.setId(id);
        appeal.setPublicationId(500L);
        appeal.setBatchId(60L);
        appeal.setCandidateId(70L);
        appeal.setRegistrationId(100L);
        appeal.setAppealNo("RA-TEST");
        appeal.setAppealReason("评分材料存在遗漏");
        appeal.setStatus(status);
        return appeal;
    }

    private CurrentUser user(String permission) {
        CurrentUser user = new CurrentUser();
        user.setAuthenticated(true);
        user.setUserId(7L);
        user.setUserUuid("user-uuid");
        user.setUsername("review-admin");
        user.setPermissions(Set.of(permission));
        return user;
    }
}
