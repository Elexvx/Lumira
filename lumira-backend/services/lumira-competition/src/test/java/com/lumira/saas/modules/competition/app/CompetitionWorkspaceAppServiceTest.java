package com.lumira.saas.modules.competition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompetitionWorkspaceAppServiceTest {
    private static final String UUID = "ca5e4e82-5be1-4d06-8aba-3c9cb45acad1";

    @Test
    void archivedWorkspacePublishesReadOnlyContractWithoutManageCapabilities() {
        CompetitionManagementRepository repository = mock(CompetitionManagementRepository.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CompetitionWorkspaceAccessPolicy policy = new CompetitionWorkspaceAccessPolicy(repository, permissionGuard);
        CompetitionWorkspaceAppService service = new CompetitionWorkspaceAppService(repository, policy);
        CurrentUser user = new CurrentUser(1L, "operator", "session", 1, true, Set.of("*"));
        user.setUserUuid("user-uuid");
        when(permissionGuard.hasPermission(eq(user), anyString())).thenAnswer(invocation ->
                user.getPermissions().contains(invocation.getArgument(1, String.class))
        );
        when(repository.findCompetitionByUuid(UUID)).thenReturn(competition("archived"));
        when(repository.countActiveRegistrations(11L)).thenReturn(2L);

        var workspace = service.getWorkspace(user, UUID);

        assertThat(workspace.isReadOnly()).isTrue();
        assertThat(workspace.getCapabilities())
                .contains("settings.read", "registration.read", "review.read", "certificate.read")
                .doesNotContain("settings.manage", "registration.manage", "review.manage", "certificate.manage");
        assertThat(workspace.getAllowedModules()).contains("settings", "registrations", "reviews", "certificates");
        assertThat(workspace.getActiveRegistrationCount()).isEqualTo(2L);
    }

    private static CompetitionVO.Competition competition(String status) {
        CompetitionVO.Competition competition = new CompetitionVO.Competition();
        competition.setId(11L);
        competition.setUuid(UUID);
        competition.setCode("AIADC-2026");
        competition.setTitle("2026 AIADC");
        competition.setStatus(status);
        return competition;
    }
}
