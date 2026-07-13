package com.lumira.team.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamInternalApiServiceTest {
    @BeforeEach
    void authenticateInternalService() {
        CurrentUser principal = new CurrentUser(0L, "internal-service", null, "internal", 0, false, Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void teamReadsRequireRequesterMembershipAndUseRepositories() {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        TeamVO.Team team = new TeamVO.Team(); team.setId(2001L); team.setTeamName("Core");
        when(teams.findTeam(2001L, 3001L, "user-uuid-3001")).thenReturn(team);
        when(members.listMembers(2001L)).thenReturn(List.of(activeMember("MEMBER")));
        TeamInternalApiService service = service(teams, members, permission, userSnapshot("ENABLED"));

        assertThat(service.getTeam(3001L, "user-uuid-3001", 2001L).getTeamName()).isEqualTo("Core");
        assertThat(service.listActiveMembers(3001L, "user-uuid-3001", 2001L)).hasSize(1);
        verify(permission, org.mockito.Mockito.times(2)).requireTeamMember(2001L, 3001L, "user-uuid-3001");
    }

    @Test
    void roleChecksUseValidatedRepositoryMembership() {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeMember(2001L, 3001L, "user-uuid-3001")).thenReturn(activeMember("ADMIN"));
        TeamInternalApiService service = service(mock(TeamRepository.class), mock(TeamMemberRepository.class), permission, userSnapshot("ENABLED"));

        assertThat(service.isTeamAdmin(2001L, 3001L, "user-uuid-3001")).isTrue();
        verify(permission).activeMember(2001L, 3001L, "user-uuid-3001");
    }

    @Test
    void invalidIdsAreRejectedBeforeRepositoryAccess() {
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = service(teams, members, permission, userSnapshot("ENABLED"));

        assertThatThrownBy(() -> service.getTeam(0L, "user-uuid-3001", 2001L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listActiveMembers(3001L, "user-uuid-3001", null)).isInstanceOf(BizException.class);
        verifyNoInteractions(teams, members, permission);
    }

    @Test
    void missingInternalPrincipalIsRejectedBeforeRepositoryAccess() {
        SecurityContextHolder.clearContext();
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = service(teams, members, permission, userSnapshot("ENABLED"));

        assertThatThrownBy(() -> service.getTeam(3001L, "user-uuid-3001", 2001L)).isInstanceOf(BizException.class);
        verifyNoInteractions(teams, members, permission);
    }

    @Test
    void disabledOrMismatchedUserIsRejectedBeforeMembershipLookup() {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService disabled = service(mock(TeamRepository.class), mock(TeamMemberRepository.class), permission, userSnapshot("DISABLED"));
        assertThatThrownBy(() -> disabled.isTeamAdmin(2001L, 3001L, "user-uuid-3001")).isInstanceOf(BizException.class);
        verifyNoInteractions(permission);
    }

    private TeamInternalApiService service(TeamRepository teams, TeamMemberRepository members, TeamPermissionService permission, SystemUserSnapshotDTO user) {
        return new TeamInternalApiService(teams, members, permission, provider(user));
    }

    private TeamVO.Member activeMember(String role) {
        TeamVO.Member member = new TeamVO.Member();
        member.setId(1L); member.setTeamId(2001L); member.setUserId(3001L); member.setUserUuid("user-uuid-3001");
        member.setRole(role); member.setStatus("ACTIVE");
        return member;
    }

    private SystemUserSnapshotDTO userSnapshot(String status) {
        return new SystemUserSnapshotDTO(3001L, "user-uuid-3001", "user3001", null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ObjectProvider<SystemInternalApi> provider(SystemUserSnapshotDTO snapshot) {
        SystemInternalApi api = mock(SystemInternalApi.class);
        when(api.findUserIdentityById(snapshot.userId())).thenReturn(snapshot);
        return new ObjectProvider<>() {
            public SystemInternalApi getObject(Object... args) { return api; }
            public SystemInternalApi getIfAvailable() { return api; }
            public SystemInternalApi getIfUnique() { return api; }
            public SystemInternalApi getObject() { return api; }
        };
    }
}
