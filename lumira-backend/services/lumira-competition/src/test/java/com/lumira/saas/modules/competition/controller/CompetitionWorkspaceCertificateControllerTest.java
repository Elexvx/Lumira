package com.lumira.saas.modules.competition.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.saas.modules.competition.app.CertificateAppService;
import com.lumira.saas.modules.competition.app.CompetitionAccessDecision;
import com.lumira.saas.modules.competition.app.CompetitionCapability;
import com.lumira.saas.modules.competition.app.CompetitionRef;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.competition.repository.CertificateRecordRepository;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompetitionWorkspaceCertificateControllerTest {
    private static final String COMPETITION_UUID = "c8c3ca4d-87b7-4c2a-81b6-0c538c700001";

    private CertificateAppService certificateAppService;
    private CompetitionWorkspaceAccessPolicy accessPolicy;
    private CurrentUser currentUser;
    private CompetitionWorkspaceCertificateController controller;

    @BeforeEach
    void setUp() {
        certificateAppService = mock(CertificateAppService.class);
        CertificateRecordRepository certificateRecordRepository = mock(CertificateRecordRepository.class);
        accessPolicy = mock(CompetitionWorkspaceAccessPolicy.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        TrustedCurrentUserResolver trustedCurrentUserResolver = mock(TrustedCurrentUserResolver.class);
        currentUser = trustedUser();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(trustedCurrentUserResolver.resolve(currentUser)).thenReturn(currentUser);
        CompetitionRef archivedCompetition = new CompetitionRef(
                42L, COMPETITION_UUID, "COMP-42", "comp-42", "赛事 42", "archived");
        when(accessPolicy.requireAccessibleCompetition(
                currentUser, COMPETITION_UUID, CompetitionCapability.CERTIFICATE_READ))
                .thenReturn(new CompetitionAccessDecision(
                        archivedCompetition, Set.of(CompetitionCapability.CERTIFICATE_READ)));
        controller = new CompetitionWorkspaceCertificateController(
                certificateAppService,
                certificateRecordRepository,
                accessPolicy,
                securityContextFacade,
                trustedCurrentUserResolver
        );
    }

    @Test
    void archivedWorkspaceCertificateReadsUseReadCapability() {
        when(certificateAppService.listPublishedAwardSources(currentUser)).thenReturn(List.of());
        when(certificateAppService.listCompetitionAwardRules(currentUser, COMPETITION_UUID, null))
                .thenReturn(List.of());

        assertThat(controller.awardSources(COMPETITION_UUID).getData()).isEmpty();
        assertThat(controller.awardRules(COMPETITION_UUID, null).getData()).isEmpty();

        verify(accessPolicy, times(2)).requireAccessibleCompetition(
                currentUser, COMPETITION_UUID, CompetitionCapability.CERTIFICATE_READ);
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
        user.setPermissions(Set.of(CompetitionWorkspaceAccessPolicy.CERTIFICATE_BATCH_CREATE));
        return user;
    }
}
