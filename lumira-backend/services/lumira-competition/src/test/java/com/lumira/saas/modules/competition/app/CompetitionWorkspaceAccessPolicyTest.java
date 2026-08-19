package com.lumira.saas.modules.competition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompetitionWorkspaceAccessPolicyTest {
    private static final String UUID = "ca5e4e82-5be1-4d06-8aba-3c9cb45acad1";

    @Test
    void normalizesOnlyCanonicalRfc4122Uuid() {
        assertThat(CompetitionWorkspaceAccessPolicy.normalizeUuid(UUID.toUpperCase())).isEqualTo(UUID);
        assertThatThrownBy(() -> CompetitionWorkspaceAccessPolicy.normalizeUuid("1-1-1-1-1"))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void registrationOnlyUserCanDiscoverPublishedWorkspaceButNotDraft() {
        CompetitionManagementRepository repository = mock(CompetitionManagementRepository.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CompetitionWorkspaceAccessPolicy policy = new CompetitionWorkspaceAccessPolicy(repository, permissionGuard);
        CurrentUser user = user("aiadc:registration:view");
        when(permissionGuard.hasPermission(eq(user), eq("aiadc:registration:view"))).thenReturn(true);
        when(permissionGuard.hasPermission(eq(user), anyString())).thenAnswer(invocation ->
                user.getPermissions().contains(invocation.getArgument(1, String.class))
        );

        CompetitionVO.Competition published = competition("published");
        when(repository.findCompetitionByUuid(UUID)).thenReturn(published);
        assertThat(policy.requireAccessibleCompetition(user, UUID, CompetitionCapability.REGISTRATION_READ).competition().status())
                .isEqualTo("published");

        CompetitionVO.Competition draft = competition("draft");
        when(repository.findCompetitionByUuid(UUID)).thenReturn(draft);
        assertThatThrownBy(() -> policy.requireAccessibleCompetition(user, UUID, CompetitionCapability.WORKSPACE_VIEW))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void archivedWorkspaceRejectsWriteCapabilityWithConflict() {
        CompetitionManagementRepository repository = mock(CompetitionManagementRepository.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CompetitionWorkspaceAccessPolicy policy = new CompetitionWorkspaceAccessPolicy(repository, permissionGuard);
        CurrentUser user = user("aiadc:competition:update");
        when(permissionGuard.hasPermission(eq(user), anyString())).thenAnswer(invocation ->
                user.getPermissions().contains(invocation.getArgument(1, String.class))
        );
        when(repository.findCompetitionByUuid(UUID)).thenReturn(competition("archived"));

        assertThatThrownBy(() -> policy.requireAccessibleCompetition(user, UUID, CompetitionCapability.SETTINGS_MANAGE))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void visibleWorkspaceWithoutRequestedModuleCapabilityReturnsForbidden() {
        CompetitionManagementRepository repository = mock(CompetitionManagementRepository.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CompetitionWorkspaceAccessPolicy policy = new CompetitionWorkspaceAccessPolicy(repository, permissionGuard);
        CurrentUser user = user("aiadc:competition:view");
        when(permissionGuard.hasPermission(eq(user), anyString())).thenAnswer(invocation ->
                user.getPermissions().contains(invocation.getArgument(1, String.class))
        );
        when(repository.findCompetitionByUuid(UUID)).thenReturn(competition("published"));

        assertThatThrownBy(() -> policy.requireAccessibleCompetition(user, UUID, CompetitionCapability.REGISTRATION_READ))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void userWithoutWorkspacePermissionCannotDiscoverPublishedWorkspace() {
        CompetitionManagementRepository repository = mock(CompetitionManagementRepository.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        CompetitionWorkspaceAccessPolicy policy = new CompetitionWorkspaceAccessPolicy(repository, permissionGuard);
        CurrentUser user = user("aiadc:unrelated:read");
        when(permissionGuard.hasPermission(eq(user), anyString())).thenAnswer(invocation ->
                user.getPermissions().contains(invocation.getArgument(1, String.class))
        );
        when(repository.findCompetitionByUuid(UUID)).thenReturn(competition("published"));

        assertThatThrownBy(() -> policy.requireAccessibleCompetition(user, UUID, CompetitionCapability.WORKSPACE_VIEW))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void registrationCapabilityExposesOneUnifiedRegistrationAndMaterialsModule() {
        CompetitionWorkspaceAccessPolicy policy = new CompetitionWorkspaceAccessPolicy(
                mock(CompetitionManagementRepository.class),
                mock(PermissionGuard.class)
        );

        assertThat(policy.allowedModules(Set.of(CompetitionCapability.REGISTRATION_READ)))
                .containsExactly("registrations");
    }

    @Test
    void archivedWorkspaceKeepsReadCapabilitiesAndFiltersEveryWriteCapability() {
        CompetitionWorkspaceAccessPolicy policy = new CompetitionWorkspaceAccessPolicy(
                mock(CompetitionManagementRepository.class),
                mock(PermissionGuard.class)
        );

        Set<CompetitionCapability> effective = policy.effectiveCapabilities(
                CompetitionRef.from(competition("archived")),
                Set.of(
                        CompetitionCapability.WORKSPACE_VIEW,
                        CompetitionCapability.REGISTRATION_READ,
                        CompetitionCapability.REGISTRATION_MANAGE,
                        CompetitionCapability.REVIEW_READ,
                        CompetitionCapability.REVIEW_MANAGE,
                        CompetitionCapability.CERTIFICATE_READ,
                        CompetitionCapability.CERTIFICATE_MANAGE,
                        CompetitionCapability.SETTINGS_READ,
                        CompetitionCapability.SETTINGS_MANAGE
                )
        );

        assertThat(effective)
                .contains(
                        CompetitionCapability.WORKSPACE_VIEW,
                        CompetitionCapability.REGISTRATION_READ,
                        CompetitionCapability.REVIEW_READ,
                        CompetitionCapability.CERTIFICATE_READ,
                        CompetitionCapability.SETTINGS_READ
                )
                .doesNotContain(
                        CompetitionCapability.REGISTRATION_MANAGE,
                        CompetitionCapability.REVIEW_MANAGE,
                        CompetitionCapability.CERTIFICATE_MANAGE,
                        CompetitionCapability.SETTINGS_MANAGE
                );
        assertThat(policy.allowedModules(effective)).contains("settings", "registrations", "reviews", "certificates");
    }

    private static CurrentUser user(String permission) {
        CurrentUser user = new CurrentUser(1L, "operator", "session", 1, true, Set.of(permission));
        user.setUserUuid("user-uuid");
        return user;
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
