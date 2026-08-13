package com.lumira.saas.modules.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.saas.modules.competition.app.CompetitionAccessDecision;
import com.lumira.saas.modules.competition.app.CompetitionCapability;
import com.lumira.saas.modules.competition.app.CompetitionRef;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.review.app.ReviewAppService;
import com.lumira.saas.modules.review.dto.ReviewDTO;
import com.lumira.saas.modules.review.repository.ReviewRepository;
import com.lumira.saas.modules.review.vo.ReviewVO;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompetitionWorkspaceReviewControllerTest {
    private static final String COMPETITION_UUID = "c8c3ca4d-87b7-4c2a-81b6-0c538c700001";

    private ReviewAppService reviewAppService;
    private ReviewRepository reviewRepository;
    private CompetitionWorkspaceAccessPolicy accessPolicy;
    private CurrentUser currentUser;
    private CompetitionWorkspaceReviewController controller;

    @BeforeEach
    void setUp() {
        reviewAppService = mock(ReviewAppService.class);
        reviewRepository = mock(ReviewRepository.class);
        accessPolicy = mock(CompetitionWorkspaceAccessPolicy.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        TrustedCurrentUserResolver trustedCurrentUserResolver = mock(TrustedCurrentUserResolver.class);
        currentUser = trustedUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(trustedCurrentUserResolver.resolve(currentUser)).thenReturn(currentUser);
        CompetitionRef competition = new CompetitionRef(42L, COMPETITION_UUID, "COMP-42", "comp-42", "赛事 42", "published");
        when(accessPolicy.requireAccessibleCompetition(currentUser, COMPETITION_UUID, CompetitionCapability.REVIEW_MANAGE))
                .thenReturn(new CompetitionAccessDecision(competition, Set.of(CompetitionCapability.REVIEW_MANAGE)));
        controller = new CompetitionWorkspaceReviewController(
                reviewAppService,
                reviewRepository,
                accessPolicy,
                securityContextFacade,
                trustedCurrentUserResolver
        );
    }

    @Test
    void revokesOnlyAnAssignmentFromABatchInsideTheSelectedCompetition() {
        ReviewVO.Batch batch = batch(31L, 42L);
        ReviewDTO.AssignmentRevokeRequest request = new ReviewDTO.AssignmentRevokeRequest();
        request.setReason("名单调整");
        ReviewVO.AdminAssignment saved = new ReviewVO.AdminAssignment();
        saved.setId(41L);
        when(reviewRepository.findBatch(31L)).thenReturn(Optional.of(batch));
        when(reviewAppService.revokeAssignment(currentUser, 31L, 41L, request)).thenReturn(saved);

        assertThat(controller.revokeAssignment(COMPETITION_UUID, 31L, 41L, request).getData())
                .isSameAs(saved);
        verify(reviewAppService).revokeAssignment(currentUser, 31L, 41L, request);
    }

    @Test
    void rejectsRevocationWhenTheBatchBelongsToAnotherCompetition() {
        ReviewDTO.AssignmentRevokeRequest request = new ReviewDTO.AssignmentRevokeRequest();
        request.setReason("名单调整");
        when(reviewRepository.findBatch(31L)).thenReturn(Optional.of(batch(31L, 99L)));

        assertThatThrownBy(() -> controller.revokeAssignment(COMPETITION_UUID, 31L, 41L, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(reviewAppService);
    }

    private ReviewVO.Batch batch(Long id, Long competitionId) {
        ReviewVO.Batch batch = new ReviewVO.Batch();
        batch.setId(id);
        batch.setCompetitionId(competitionId);
        return batch;
    }

    private CurrentUser trustedUser() {
        CurrentUser user = new CurrentUser();
        user.setUserId(1001L);
        user.setUserUuid("user-uuid-1001");
        user.setUsername("operator");
        user.setSessionId("session-1");
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-1");
        user.setAuthenticated(true);
        user.setPermissions(Set.of(ReviewAppService.ASSIGNMENT_MANAGE));
        return user;
    }
}
