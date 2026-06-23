package com.lumira.team.app;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamAppServiceTest {
    @Test
    void createTeamShouldDelegatePersistenceToRepositories() {
        Fixtures fixtures = fixtures("OWNER");
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");
        TeamDTO.DraftMemberRequest draftMember = draftMember("Alice");
        request.setInitialMembers(List.of(draftMember));

        TeamVO.Team team = fixtures.service.createTeam(currentUser(3001L), request);

        assertThat(team.getId()).isEqualTo(2001L);
        verify(fixtures.teamRepository).nextTeamCode(1001L);
        verify(fixtures.teamRepository).createTeam(anyLong(), any(), anyLong(), any());
        verify(fixtures.teamMemberRepository).addOwner(1001L, 2001L, 3001L);
        verify(fixtures.teamMemberRepository).addDraftMember(anyLong(), anyLong(), any());
        verify(fixtures.teamMemberRepository).refreshMemberCount(1001L, 2001L);
    }

    @Test
    void managerCanAddDraftMemberWithoutRegisteredUser() {
        Fixtures fixtures = fixtures("MANAGER");
        TeamDTO.MemberCreateRequest request = new TeamDTO.MemberCreateRequest();
        request.setMemberName("External Member");
        request.setEmployeeNo("E001");
        request.setDepartmentName("Design");
        request.setRole("MEMBER");
        when(fixtures.teamMemberRepository.addDraftMember(anyLong(), anyLong(), any())).thenReturn(9L);
        when(fixtures.teamMemberRepository.findMemberById(1001L, 2001L, 9L)).thenReturn(member(9L, null, "MEMBER"));

        TeamVO.Member member = fixtures.service.addMember(currentUser(3001L), 2001L, request);

        assertThat(member.getId()).isEqualTo(9L);
        verify(fixtures.teamMemberRepository).addDraftMember(anyLong(), anyLong(), any());
        verify(fixtures.teamMemberRepository).refreshMemberCount(1001L, 2001L);
    }

    @Test
    void myTeamsShouldUseMembershipRepositoryScope() {
        Fixtures fixtures = fixtures("MEMBER");

        List<TeamVO.Team> teams = fixtures.service.myTeams(currentUser(3001L));

        assertThat(teams).hasSize(1);
        verify(fixtures.teamRepository).listMyTeams(1001L, 3001L);
    }

    @Test
    void adminTeamsShouldUseRepositoryWithoutRoleGate() {
        Fixtures fixtures = fixtures("MEMBER");

        List<TeamVO.Team> teams = fixtures.service.listTeamsForAdmin(currentUser(3001L));

        assertThat(teams).hasSize(1);
        verify(fixtures.teamRepository).listTeamsForAdmin(1001L, 3001L);
    }

    @Test
    void ownerCanUpdateButMemberCannot() {
        TeamDTO.TeamUpdateRequest request = updateRequest();
        Fixtures owner = fixtures("OWNER");
        assertThat(owner.service.updateTeam(currentUser(3001L), 2001L, request).getTeamName()).isEqualTo("Core Team");
        verify(owner.teamRepository).updateTeamProfile(anyLong(), anyLong(), anyLong(), any());

        Fixtures member = fixtures("MEMBER");
        assertThatThrownBy(() -> member.service.updateTeam(currentUser(3001L), 2001L, request))
                .isInstanceOf(BizException.class);
    }

    @Test
    void adminUpdateBypassesTeamRole() {
        Fixtures fixtures = fixtures("MEMBER");

        assertThat(fixtures.service.updateTeamForAdmin(currentUser(3001L), 2001L, updateRequest()).getTeamName()).isEqualTo("Core Team");

        verify(fixtures.teamRepository).updateTeamProfile(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void ownerCannotLeaveDirectly() {
        Fixtures fixtures = fixtures("OWNER");
        when(fixtures.permissionService.activeMember(1001L, 2001L, 3001L)).thenReturn(member(1L, 3001L, "OWNER"));

        assertThatThrownBy(() -> fixtures.service.leaveTeam(currentUser(3001L), 2001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Owner must transfer");
    }

    @Test
    void transferOwnerShouldDemotePreviousOwnerThroughRepository() {
        Fixtures fixtures = fixtures("OWNER");
        when(fixtures.teamMemberRepository.findMemberById(1001L, 2001L, 2L)).thenReturn(member(2L, 3002L, "ADMIN"));
        TeamDTO.TransferOwnerRequest request = new TeamDTO.TransferOwnerRequest();
        request.setMemberId(2L);
        request.setPreviousOwnerRole("MEMBER");

        fixtures.service.transferOwner(currentUser(3001L), 2001L, request);

        verify(fixtures.teamMemberRepository).transferOwner(1001L, 2001L, 3001L, "MEMBER", 2L);
        verify(fixtures.teamRepository).transferOwner(1001L, 2001L, 3002L, 3001L);
    }

    private Fixtures fixtures(String role) {
        TeamRepository teamRepository = mock(TeamRepository.class);
        TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
        TeamInviteRepository teamInviteRepository = mock(TeamInviteRepository.class);
        TeamJoinRequestRepository teamJoinRequestRepository = mock(TeamJoinRequestRepository.class);
        TeamPermissionService permissionService = mockPermission(role);
        when(teamRepository.nextTeamCode(1001L)).thenReturn("T001");
        when(teamRepository.createTeam(anyLong(), any(), anyLong(), any())).thenReturn(2001L);
        when(teamRepository.listMyTeams(1001L, 3001L)).thenReturn(List.of(team()));
        when(teamRepository.listTeamsForAdmin(1001L, 3001L)).thenReturn(List.of(team()));
        when(teamRepository.findTeam(1001L, 2001L, 3001L)).thenReturn(team());
        when(teamRepository.updateTeamProfile(anyLong(), anyLong(), anyLong(), any())).thenReturn(1);
        when(teamRepository.loadEnabledDictValues(anyLong(), any())).thenReturn(Set.of());
        when(teamMemberRepository.findMemberById(1001L, 2001L, 2L)).thenReturn(member(2L, 3002L, "ADMIN"));
        TeamAppService service = new TeamAppService(
                teamRepository,
                teamMemberRepository,
                teamInviteRepository,
                teamJoinRequestRepository,
                permissionService,
                (tenantId, userId, username, moduleName, actionName, operationType, resultStatus, detailMessage) -> {}
        );
        return new Fixtures(service, teamRepository, teamMemberRepository, permissionService);
    }

    private TeamPermissionService mockPermission(String role) {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeRole(1001L, 2001L, 3001L)).thenReturn(role);
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
        user.setUsername("user" + userId);
        user.setCurrentTenantId(1001L);
        return user;
    }

    private static TeamVO.Team team() {
        TeamVO.Team team = new TeamVO.Team();
        team.setId(2001L);
        team.setTenantId(1001L);
        team.setTeamCode("T001");
        team.setTeamName("Core Team");
        team.setTeamType("GENERAL");
        team.setVisibility("PRIVATE");
        team.setJoinMode("INVITE_ONLY");
        team.setOwnerUserId(3001L);
        team.setMemberCount(1);
        team.setStatus("ACTIVE");
        team.setMyRole("MEMBER");
        return team;
    }

    private static TeamVO.Member member(Long id, Long userId, String role) {
        TeamVO.Member member = new TeamVO.Member();
        member.setId(id);
        member.setTenantId(1001L);
        member.setTeamId(2001L);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        return member;
    }

    private record Fixtures(
            TeamAppService service,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamPermissionService permissionService
    ) {}
}
