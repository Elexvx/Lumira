package com.lumira.team.app;

import com.lumira.common.exception.BizException;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamPermissionServiceTest {
    @Test
    void roleBoundariesShouldMatchTeamRules() {
        TeamPermissionService service = new TeamPermissionService(repository("OWNER"));

        service.requireTeamOwner(2001L, 3001L, "user-uuid-3001");
        assertThat(service.canInvite("OWNER")).isTrue();
        assertThat(service.canInvite("ADMIN")).isTrue();
        assertThat(service.canInvite("MEMBER")).isFalse();
        assertThat(service.canUpdateTeam("MANAGER")).isTrue();
        assertThat(service.canDisbandTeam("ADMIN")).isFalse();
        assertThat(service.canRemoveMember("ADMIN", "OWNER", false)).isFalse();
        assertThat(service.canRemoveMember("ADMIN", "ADMIN", false)).isFalse();
        assertThat(service.canRemoveMember("ADMIN", "ADMIN", true)).isTrue();
        assertThat(service.canRemoveMember("ADMIN", "MANAGER", false)).isTrue();
        assertThat(service.canRemoveMember("MANAGER", "ADMIN", false)).isFalse();
        assertThat(service.canRemoveMember("MANAGER", "MEMBER", false)).isTrue();
        assertThat(service.canRemoveMember("MANAGER", "MANAGER", true)).isTrue();
        assertThat(service.canRemoveMember("MEMBER", "MEMBER", true)).isTrue();
    }

    @Test
    void memberCannotInviteOrRequireAdmin() {
        TeamPermissionService service = new TeamPermissionService(repository("MEMBER"));

        assertThatThrownBy(() -> service.requireTeamAdmin(2001L, 3001L, "user-uuid-3001"))
                .isInstanceOf(BizException.class);
        assertThat(service.canInvite("MEMBER")).isFalse();
    }

    @Test
    void permissionLookupShouldRejectInvalidIdsBeforeQuerying() {
        TeamMemberRepository repository = mock(TeamMemberRepository.class);
        TeamPermissionService service = new TeamPermissionService(repository);

        assertThatThrownBy(() -> service.activeMember(0L, 3001L, "user-uuid-3001"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.requireTeamMember(2001L, null, "user-uuid-3001"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(repository);
    }

    private TeamMemberRepository repository(String role) {
        TeamMemberRepository repository = mock(TeamMemberRepository.class);
        TeamVO.Member member = new TeamVO.Member();
        member.setId(1L);
        member.setTeamId(2001L);
        member.setUserId(3001L);
        member.setUserUuid("user-uuid-3001");
        member.setRole(role);
        member.setStatus("ACTIVE");
        when(repository.findActiveMember(2001L, 3001L, "user-uuid-3001")).thenReturn(member);
        return repository;
    }
}
