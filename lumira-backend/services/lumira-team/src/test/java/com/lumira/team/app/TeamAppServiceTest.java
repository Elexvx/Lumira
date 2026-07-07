package com.lumira.team.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamAppServiceTest {
    @Test
    void myTeamsShouldRejectUnauthenticatedUserBeforeRepositoryAccess() {
        Fixtures fixtures = fixtures("MEMBER");

        assertThatThrownBy(() -> fixtures.service.myTeams(unauthenticatedUser(3001L)))
                .isInstanceOf(BizException.class);

        verify(fixtures.teamRepository, never()).listMyTeams(anyLong(), any());
        verifyNoInteractions(fixtures.teamMemberRepository);
    }

    @Test
    void myTeamsShouldRejectUserWithoutUsernameBeforeRepositoryAccess() {
        Fixtures fixtures = fixtures("MEMBER");
        CurrentUser user = currentUser(3001L);
        user.setUsername(" ");

        assertThatThrownBy(() -> fixtures.service.myTeams(user))
                .isInstanceOf(BizException.class);

        verify(fixtures.teamRepository, never()).listMyTeams(anyLong(), any());
        verifyNoInteractions(fixtures.teamMemberRepository);
    }

    @Test
    void myTeamsShouldRejectMissingSessionVersionBeforeRepositoryAccess() {
        Fixtures fixtures = fixtures("MEMBER");
        CurrentUser user = currentUser(3001L);
        user.setSessionVersion(null);

        assertThatThrownBy(() -> fixtures.service.myTeams(user))
                .isInstanceOf(BizException.class);

        verify(fixtures.teamRepository, never()).listMyTeams(anyLong(), any());
        verifyNoInteractions(fixtures.teamMemberRepository);
    }

    @Test
    void myTeamsShouldRejectMissingUserUuidBeforeRepositoryAccess() {
        Fixtures fixtures = fixtures("MEMBER");
        CurrentUser user = currentUser(3001L);
        user.setUserUuid(null);

        assertThatThrownBy(() -> fixtures.service.myTeams(user))
                .isInstanceOf(BizException.class);

        verify(fixtures.teamRepository, never()).listMyTeams(anyLong(), any());
        verifyNoInteractions(fixtures.teamMemberRepository);
    }

    @Test
    void myTeamsShouldRejectMissingPermissionsVersionBeforeRepositoryAccess() {
        Fixtures fixtures = fixtures("MEMBER");
        CurrentUser user = currentUser(3001L);
        user.setPermissionsVersion(" ");

        assertThatThrownBy(() -> fixtures.service.myTeams(user))
                .isInstanceOf(BizException.class);

        verify(fixtures.teamRepository, never()).listMyTeams(anyLong(), any());
        verifyNoInteractions(fixtures.teamMemberRepository);
    }

    @Test
    void teamOperationsShouldRejectInvalidResourceIdsBeforePermissionOrRepositoryAccess() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MEMBER", snapshot(Set.of("team:member:role-update")));

        assertThatThrownBy(() -> fixtures.service.getTeam(currentUser(3001L), 0L))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> fixtures.service.updateMemberRole(currentUser(3001L, "team:member:role-update"), 2001L, -1L, new TeamDTO.MemberRoleRequest()))
                .isInstanceOf(BizException.class);

        verify(fixtures.permissionService, never()).activeRole(anyLong(), anyLong(), any());
        verify(fixtures.teamRepository, never()).findTeam(anyLong(), anyLong(), any());
        verify(fixtures.teamMemberRepository, never()).findMemberById(anyLong(), anyLong());
    }

    @Test
    void updateMemberRoleShouldRejectInvalidRequestBeforePermissionOrMemberLookup() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of("team:member:role-update")));

        assertThatThrownBy(() -> fixtures.service.updateMemberRole(currentUser(3001L, "team:member:role-update"), 2001L, 2L, null))
                .isInstanceOf(BizException.class);

        verify(fixtures.permissionService, never()).activeRole(anyLong(), anyLong(), any());
        verify(fixtures.teamMemberRepository, never()).findMemberById(anyLong(), anyLong());
    }

    @Test
    void createTeamShouldDelegatePersistenceToRepositories() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of("team:create")));
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");
        TeamDTO.DraftMemberRequest draftMember = draftMember("Alice");
        request.setInitialMembers(List.of(draftMember));

        TeamVO.Team team = fixtures.service.createTeam(currentUser(3001L, "team:create"), request);

        assertThat(team.getId()).isEqualTo(2001L);
        verify(fixtures.teamRepository).nextTeamCode();
        verify(fixtures.teamRepository).createTeam(any(), anyLong(), any(), any());
        verify(fixtures.teamMemberRepository).addOwner(2001L, 3001L, "user-uuid-3001");
        verify(fixtures.teamMemberRepository).addDraftMember(anyLong(), any());
        verify(fixtures.teamMemberRepository).refreshMemberCount(eq(2001L), any(TeamVO.Team.class));
    }

    @Test
    void createTeamShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeRepositoryAccess() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of()));
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");

        assertThatThrownBy(() -> fixtures.service.createTeam(currentUser(3001L, "team:create"), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixtures.teamRepository, never()).createTeam(any(), anyLong(), any(), any());
        verify(fixtures.teamMemberRepository, never()).addOwner(anyLong(), anyLong(), any());
    }

    @Test
    void createTeamShouldRejectTooManyInitialMembersBeforeRepositoryAccess() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of("team:create")));
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");
        request.setInitialMembers(IntStream.range(0, 101)
                .mapToObj(index -> draftMember("Member " + index))
                .toList());

        assertThatThrownBy(() -> fixtures.service.createTeam(currentUser(3001L, "team:create"), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Initial members");

        verify(fixtures.teamRepository, never()).createTeam(any(), anyLong(), any(), any());
        verify(fixtures.teamMemberRepository, never()).addOwner(anyLong(), anyLong(), any());
    }

    @Test
    void managerCanAddDraftMemberWithoutRegisteredUser() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MANAGER", snapshot(Set.of("team:member:invite")));
        TeamDTO.MemberCreateRequest request = new TeamDTO.MemberCreateRequest();
        request.setMemberName("External Member");
        request.setEmployeeNo("E001");
        request.setDepartmentName("Design");
        request.setRole("MEMBER");
        when(fixtures.teamMemberRepository.addDraftMember(anyLong(), any())).thenReturn(9L);
        when(fixtures.teamMemberRepository.findMemberById(2001L, 9L)).thenReturn(member(9L, null, "MEMBER"));

        TeamVO.Member member = fixtures.service.addMember(currentUser(3001L, "team:member:invite"), 2001L, request);

        assertThat(member.getId()).isEqualTo(9L);
        verify(fixtures.teamMemberRepository).addDraftMember(anyLong(), any());
        verify(fixtures.teamMemberRepository).refreshMemberCount(eq(2001L), any(TeamVO.Team.class));
    }

    @Test
    void addDraftMemberNormalizesExtraValues() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MANAGER", snapshot(Set.of("team:member:invite")));
        TeamDTO.MemberCreateRequest request = new TeamDTO.MemberCreateRequest();
        request.setMemberName("External Member");
        request.setRole("MEMBER");
        request.setExtraValues(Map.of(" shirtSize ", " L ", "empty", " "));
        when(fixtures.teamMemberRepository.addDraftMember(anyLong(), any())).thenReturn(9L);
        when(fixtures.teamMemberRepository.findMemberById(2001L, 9L)).thenReturn(member(9L, null, "MEMBER"));

        fixtures.service.addMember(currentUser(3001L, "team:member:invite"), 2001L, request);

        ArgumentCaptor<TeamDTO.DraftMemberRequest> captor = ArgumentCaptor.forClass(TeamDTO.DraftMemberRequest.class);
        verify(fixtures.teamMemberRepository).addDraftMember(anyLong(), captor.capture());
        assertThat(captor.getValue().getExtraValues()).containsExactlyEntriesOf(Map.of("shirtSize", "L"));
    }

    @Test
    void addDraftMemberShouldRejectUnboundedExtraValuesBeforePersistence() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MANAGER", snapshot(Set.of("team:member:invite")));
        TeamDTO.MemberCreateRequest request = new TeamDTO.MemberCreateRequest();
        request.setMemberName("External Member");
        request.setExtraValues(IntStream.range(0, 21)
                .boxed()
                .collect(java.util.stream.Collectors.toMap(index -> "k" + index, index -> "v" + index)));

        assertThatThrownBy(() -> fixtures.service.addMember(currentUser(3001L, "team:member:invite"), 2001L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Extra values");

        verify(fixtures.teamMemberRepository, never()).addDraftMember(anyLong(), any());
    }

    @Test
    void myTeamsShouldUseMembershipRepositoryScope() {
        Fixtures fixtures = fixtures("MEMBER");

        List<TeamVO.Team> teams = fixtures.service.myTeams(currentUser(3001L));

        assertThat(teams).hasSize(1);
        verify(fixtures.teamRepository).listMyTeams(3001L, "user-uuid-3001");
    }

    @Test
    void adminTeamsShouldRequireViewPermissionAtServiceLayer() {
        Fixtures fixtures = fixtures("MEMBER");

        assertThatThrownBy(() -> fixtures.service.listTeamsForAdmin(currentUser(3001L)))
                .isInstanceOf(BizException.class);

        verify(fixtures.teamRepository, never()).listTeamsForAdmin(anyLong(), any());
    }

    @Test
    void adminTeamsShouldUseRepositoryWhenPermissionIsPresent() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MEMBER", snapshot(Set.of("team:view")));

        List<TeamVO.Team> teams = fixtures.service.listTeamsForAdmin(currentUser(3001L, "team:view"));

        assertThat(teams).hasSize(1);
        verify(fixtures.teamRepository).listTeamsForAdmin(3001L, "user-uuid-3001");
    }

    @Test
    void adminTeamsShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeRepositoryAccess() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MEMBER", snapshot(Set.of("team:update")));

        assertThatThrownBy(() -> fixtures.service.listTeamsForAdmin(currentUser(3001L, "team:view")))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixtures.teamRepository, never()).listTeamsForAdmin(anyLong(), any());
        verify(fixtures.systemInternalApi, times(3)).findUserIdentityById(3001L);
        verify(fixtures.systemInternalApi, times(3)).permissionSnapshot(3001L, "user-uuid-3001");
    }

    @Test
    void listMembersShouldRejectWhenLiveSnapshotRevokesMemberViewPermissionBeforeRepositoryAccess() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of()));

        assertThatThrownBy(() -> fixtures.service.listMembers(currentUser(3001L, "team:member:view"), 2001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixtures.teamMemberRepository, never()).listMembers(anyLong());
        verify(fixtures.permissionService, never()).requireTeamMember(anyLong(), anyLong(), any());
    }

    @Test
    void myTeamsShouldRejectWhenTrustedUserIsDisabledBeforeRepositoryAccess() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MEMBER", null);
        when(fixtures.systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user3001", "DISABLED"));

        assertThatThrownBy(() -> fixtures.service.myTeams(currentUser(3001L)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(fixtures.teamRepository, never()).listMyTeams(anyLong(), any());
        verify(fixtures.systemInternalApi, never()).permissionSnapshot(3001L, "user-uuid-3001");
        verify(fixtures.permissionService, never()).activeRole(anyLong(), anyLong(), any());
    }

    @Test
    void myTeamsShouldRejectWhenTrustedUsernameIsUnavailableBeforeRepositoryAccess() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MEMBER", null);
        when(fixtures.systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, " ", "ENABLED"));

        assertThatThrownBy(() -> fixtures.service.myTeams(currentUser(3001L)))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(fixtures.teamRepository, never()).listMyTeams(anyLong(), any());
        verify(fixtures.systemInternalApi, never()).permissionSnapshot(3001L, "user-uuid-3001");
        verify(fixtures.permissionService, never()).activeRole(anyLong(), anyLong(), any());
    }

    @Test
    void ownerCanUpdateButMemberCannot() {
        TeamDTO.TeamUpdateRequest request = updateRequest();
        Fixtures owner = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of("team:update")));
        assertThat(owner.service.updateTeam(currentUser(3001L, "team:update"), 2001L, request).getTeamName()).isEqualTo("Core Team");
        verify(owner.teamRepository).updateTeamProfile(eq(2001L), any(TeamVO.Team.class), eq(3001L), eq("user-uuid-3001"), any());

        Fixtures member = fixturesWithLiveSnapshot("MEMBER", snapshot(Set.of("team:update")));
        assertThatThrownBy(() -> member.service.updateTeam(currentUser(3001L, "team:update"), 2001L, request))
                .isInstanceOf(BizException.class);
    }

    @Test
    void adminUpdateBypassesTeamRole() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MEMBER", snapshot(Set.of()));

        assertThatThrownBy(() -> fixtures.service.updateTeamForAdmin(currentUser(3001L), 2001L, updateRequest()))
                .isInstanceOf(BizException.class);
        verify(fixtures.teamRepository, never()).updateTeamProfile(anyLong(), any(), anyLong(), any(), any());

        Fixtures permittedFixtures = fixturesWithLiveSnapshot("MEMBER", snapshot(Set.of("team:update")));

        assertThat(permittedFixtures.service.updateTeamForAdmin(currentUser(3001L, "team:update"), 2001L, updateRequest()).getTeamName()).isEqualTo("Core Team");

        verify(permittedFixtures.teamRepository).updateTeamProfile(anyLong(), any(TeamVO.Team.class), anyLong(), eq("user-uuid-3001"), any());
    }

    @Test
    void adminDeleteShouldRequireDeletePermissionAtServiceLayer() {
        Fixtures fixtures = fixtures("MEMBER");

        assertThatThrownBy(() -> fixtures.service.deleteTeamForAdmin(currentUser(3001L), 2001L))
                .isInstanceOf(BizException.class);

        verify(fixtures.teamRepository, never()).softDeleteTeam(anyLong(), any(), anyLong(), any());
    }

    @Test
    void removeMemberShouldRejectWhenLiveSnapshotRevokesMemberRemovePermissionBeforeMemberLookup() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MANAGER", snapshot(Set.of()));

        assertThatThrownBy(() -> fixtures.service.removeMember(currentUser(3001L, "team:member:remove"), 2001L, 9L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixtures.permissionService, never()).activeMember(anyLong(), anyLong(), any());
        verify(fixtures.teamMemberRepository, never()).findMemberById(anyLong(), anyLong());
    }

    @Test
    void deleteTeamShouldPassTrustedDeletedTeamSnapshotToChildRepositories() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of("team:delete")));
        when(fixtures.teamRepository.softDeleteTeam(anyLong(), any(), anyLong(), any())).thenReturn(1);

        assertThat(fixtures.service.deleteTeam(currentUser(3001L, "team:delete"), 2001L)).isTrue();

        ArgumentCaptor<TeamVO.Team> teamCaptor = ArgumentCaptor.forClass(TeamVO.Team.class);
        verify(fixtures.teamMemberRepository).removeMembersByTeam(eq(2001L), teamCaptor.capture());
        TeamVO.Team trustedTeam = teamCaptor.getValue();
        assertThat(trustedTeam.getOwnerUserId()).isEqualTo(3001L);
        assertThat(trustedTeam.getOwnerUserUuid()).isEqualTo("user-uuid-3001");
        verify(fixtures.teamInviteRepository).disableInvitesByTeam(2001L, trustedTeam, 3001L, "user-uuid-3001");
        verify(fixtures.teamJoinRequestRepository).closeRequestsByTeam(2001L, trustedTeam);
    }

    @Test
    void ownerCannotLeaveDirectly() {
        Fixtures fixtures = fixtures("OWNER");
        when(fixtures.permissionService.activeMember(2001L, 3001L, "user-uuid-3001")).thenReturn(member(1L, 3001L, "OWNER"));

        assertThatThrownBy(() -> fixtures.service.leaveTeam(currentUser(3001L), 2001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Owner must transfer");
    }

    @Test
    void managerCanRemoveDraftMemberWithoutRegisteredUser() {
        Fixtures fixtures = fixturesWithLiveSnapshot("MANAGER", snapshot(Set.of("team:member:remove")));
        when(fixtures.permissionService.activeMember(2001L, 3001L, "user-uuid-3001")).thenReturn(member(1L, 3001L, "MANAGER"));
        when(fixtures.permissionService.canRemoveMember("MANAGER", "MEMBER", false)).thenReturn(true);
        TeamVO.Member target = member(9L, null, "MEMBER");
        when(fixtures.teamMemberRepository.findMemberById(2001L, 9L)).thenReturn(target);
        when(fixtures.teamMemberRepository.removeMember(2001L, target)).thenReturn(true);

        assertThat(fixtures.service.removeMember(currentUser(3001L, "team:member:remove"), 2001L, 9L)).isTrue();

        verify(fixtures.teamMemberRepository).removeMember(2001L, target);
        verify(fixtures.teamMemberRepository).refreshMemberCount(eq(2001L), any(TeamVO.Team.class));
    }

    @Test
    void adminCannotChangePeerAdminRoles() {
        Fixtures fixtures = fixturesWithLiveSnapshot("ADMIN", snapshot(Set.of("team:member:role-update")));
        TeamDTO.MemberRoleRequest request = new TeamDTO.MemberRoleRequest();
        request.setRole("MANAGER");
        when(fixtures.teamMemberRepository.findMemberById(2001L, 2L)).thenReturn(member(2L, 3002L, "ADMIN"));

        assertThatThrownBy(() -> fixtures.service.updateMemberRole(currentUser(3001L, "team:member:role-update"), 2001L, 2L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Only team owner");

        verify(fixtures.teamMemberRepository, never()).updateMemberRole(anyLong(), any(), any());
    }

    @Test
    void adminCannotPromoteMemberToAdmin() {
        Fixtures fixtures = fixturesWithLiveSnapshot("ADMIN", snapshot(Set.of("team:member:role-update")));
        TeamDTO.MemberRoleRequest request = new TeamDTO.MemberRoleRequest();
        request.setRole("ADMIN");
        when(fixtures.teamMemberRepository.findMemberById(2001L, 9L)).thenReturn(member(9L, 3009L, "MEMBER"));

        assertThatThrownBy(() -> fixtures.service.updateMemberRole(currentUser(3001L, "team:member:role-update"), 2001L, 9L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Only team owner");

        verify(fixtures.teamMemberRepository, never()).updateMemberRole(anyLong(), any(), any());
    }

    @Test
    void transferOwnerShouldDemotePreviousOwnerThroughRepository() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of("team:member:role-update")));
        when(fixtures.teamMemberRepository.findMemberById(2001L, 2L)).thenReturn(member(2L, 3002L, "ADMIN"));
        TeamDTO.TransferOwnerRequest request = new TeamDTO.TransferOwnerRequest();
        request.setMemberId(2L);
        request.setPreviousOwnerRole("MEMBER");

        fixtures.service.transferOwner(currentUser(3001L, "team:member:role-update"), 2001L, request);

        verify(fixtures.teamMemberRepository).transferOwner(2001L, 3001L, "user-uuid-3001", "MEMBER", 2L, 3002L, "user-uuid-3002");
        verify(fixtures.teamRepository).transferOwner(2001L, 3001L, "user-uuid-3001", 3002L, "user-uuid-3002", 3001L, "user-uuid-3001");
    }

    @Test
    void transferOwnerShouldRejectWhenLiveSnapshotRevokesRoleUpdatePermissionBeforeOwnerCheck() {
        Fixtures fixtures = fixturesWithLiveSnapshot("OWNER", snapshot(Set.of()));
        TeamDTO.TransferOwnerRequest request = new TeamDTO.TransferOwnerRequest();
        request.setMemberId(2L);

        assertThatThrownBy(() -> fixtures.service.transferOwner(currentUser(3001L, "team:member:role-update"), 2001L, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(fixtures.permissionService, never()).requireTeamOwner(anyLong(), anyLong(), any());
        verify(fixtures.teamRepository, never()).transferOwner(anyLong(), anyLong(), any(), anyLong(), any(), anyLong(), any());
    }

    private Fixtures fixtures(String role) {
        return fixturesWithLiveSnapshot(role, snapshot(Set.of()));
    }

    private Fixtures fixturesWithLiveSnapshot(String role, PermissionSnapshotDTO snapshot) {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(3001L))
                .thenReturn(userSnapshot(3001L, "user3001", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(eq(3001L), eq("user-uuid-3001")))
                .thenReturn(snapshot == null ? snapshot(Set.of()) : snapshot);
        ObjectProvider<SystemInternalApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(systemInternalApi);
        return fixtures(role, provider);
    }

    private Fixtures fixtures(String role, ObjectProvider<SystemInternalApi> systemInternalApiProvider) {
        TeamRepository teamRepository = mock(TeamRepository.class);
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamInviteRepository teamInviteRepository = mock(TeamInviteRepository.class);
        TeamJoinRequestRepository teamJoinRequestRepository = mock(TeamJoinRequestRepository.class);
        TeamPermissionService permissionService = mockPermission(role);
        when(teamRepository.nextTeamCode()).thenReturn("T001");
        when(teamRepository.createTeam(any(), anyLong(), any(), any())).thenReturn(2001L);
        when(teamRepository.listMyTeams(3001L, "user-uuid-3001")).thenReturn(List.of(team()));
        when(teamRepository.listTeamsForAdmin(3001L, "user-uuid-3001")).thenReturn(List.of(team()));
        when(teamRepository.findTeam(2001L, 3001L, "user-uuid-3001")).thenReturn(team());
        when(teamRepository.findTeam(2001L, null, null)).thenReturn(team());
        when(teamRepository.updateTeamProfile(anyLong(), any(), anyLong(), any(), any())).thenReturn(1);
        when(teamRepository.transferOwner(anyLong(), anyLong(), any(), anyLong(), any(), anyLong(), any())).thenReturn(1);
        when(teamMemberRepository.transferOwner(anyLong(), anyLong(), any(), any(), anyLong(), anyLong(), any())).thenReturn(true);
        when(teamRepository.loadEnabledDictValues(any())).thenReturn(Set.of());
        when(teamMemberRepository.findMemberById(2001L, 2L)).thenReturn(member(2L, 3002L, "ADMIN"));
        TeamAppService service = new TeamAppService(
                teamRepository,
                teamMemberRepository,
                teamInviteRepository,
                teamJoinRequestRepository,
                permissionService,
                (userId, userUuid, username, moduleName, actionName, operationType, resultStatus, detailMessage) -> {},
                systemInternalApiProvider
        );
        return new Fixtures(
                service,
                teamRepository,
                teamMemberRepository,
                teamInviteRepository,
                teamJoinRequestRepository,
                permissionService,
                systemInternalApiProvider == null ? null : systemInternalApiProvider.getIfAvailable()
        );
    }

    private TeamPermissionService mockPermission(String role) {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeRole(2001L, 3001L, "user-uuid-3001")).thenReturn(role);
        when(permission.canUpdateTeam("OWNER")).thenReturn(true);
        when(permission.canUpdateTeam("ADMIN")).thenReturn(true);
        when(permission.canUpdateTeam("MANAGER")).thenReturn(true);
        when(permission.canUpdateTeam("MEMBER")).thenReturn(false);
        return permission;
    }

    private static TeamDTO.TeamUpdateRequest updateRequest() {
        TeamDTO.TeamUpdateRequest request = new TeamDTO.TeamUpdateRequest();
        request.setTeamName("Updated");
        return request;
    }

    private static TeamDTO.DraftMemberRequest draftMember(String name) {
        TeamDTO.DraftMemberRequest request = new TeamDTO.DraftMemberRequest();
        request.setMemberName(name);
        request.setRole("MEMBER");
        return request;
    }

    private static CurrentUser currentUser(Long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setUserUuid("user-uuid-" + userId);
        user.setUsername("user" + userId);
        user.setSessionId("session-" + userId);
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-1");
        user.setAuthenticated(true);
        return user;
    }

    private static CurrentUser currentUser(Long userId, String... permissions) {
        CurrentUser user = currentUser(userId);
        user.setPermissions(Set.of(permissions));
        return user;
    }

    private static CurrentUser unauthenticatedUser(Long userId) {
        CurrentUser user = currentUser(userId);
        user.setAuthenticated(false);
        return user;
    }

    private static TeamVO.Team team() {
        TeamVO.Team team = new TeamVO.Team();
        team.setId(2001L);
        team.setTeamCode("T001");
        team.setTeamName("Core Team");
        team.setTeamType("GENERAL");
        team.setVisibility("PRIVATE");
        team.setJoinMode("INVITE_ONLY");
        team.setOwnerUserId(3001L);
        team.setOwnerUserUuid("user-uuid-3001");
        team.setMemberCount(1);
        team.setStatus("ACTIVE");
        team.setMyRole("MEMBER");
        return team;
    }

    private static TeamVO.Member member(Long id, Long userId, String role) {
        TeamVO.Member member = new TeamVO.Member();
        member.setId(id);
        member.setTeamId(2001L);
        member.setUserId(userId);
        member.setUserUuid("user-uuid-" + userId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        return member;
    }

    private static PermissionSnapshotDTO snapshot(Set<String> permissions) {
        return new PermissionSnapshotDTO(
                "permissions-2",
                permissions.stream().sorted().toList(),
                List.of(7001L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/dashboard/home"
        );
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                "user-uuid-" + userId,
                username,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private record Fixtures(
            TeamAppService service,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamInviteRepository teamInviteRepository,
            TeamJoinRequestRepository teamJoinRequestRepository,
            TeamPermissionService permissionService,
            SystemInternalApi systemInternalApi
    ) {}
}
