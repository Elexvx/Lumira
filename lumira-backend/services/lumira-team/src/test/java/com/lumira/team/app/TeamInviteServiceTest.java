package com.lumira.team.app;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamInviteServiceTest {
    @Test
    void createInviteShouldReturnRawTokenAndPersistOnlyHash() {
        Fixtures fixtures = fixtures();
        TeamDTO.InviteCreateRequest request = new TeamDTO.InviteCreateRequest();
        request.setInviteCode("JOIN2026");

        TeamVO.Invite invite = fixtures.service.createInvite(currentUser(3001L), 2001L, request);

        assertThat(invite.getRawToken()).isNotBlank();
        assertThat(invite.getInviteUrl()).contains(invite.getRawToken());
        verify(fixtures.teamInviteRepository).createInvite(
                anyLong(), anyLong(), anyString(), org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"),
                anyString(), anyString(), any(), any(), anyBoolean(), anyLong()
        );
    }

    @Test
    void blankInviteCodeShouldBeGeneratedAndJoinable() {
        Fixtures fixtures = fixtures();
        TeamDTO.InviteCreateRequest request = new TeamDTO.InviteCreateRequest();
        when(fixtures.permissionService.activeMember(1001L, 2001L, 3002L)).thenReturn(null);

        TeamVO.Invite invite = fixtures.service.createInvite(currentUser(3001L), 2001L, request);
        when(fixtures.teamInviteRepository.findByCode(1001L, invite.getInviteCode())).thenReturn(invite);
        TeamVO.JoinResult result = fixtures.service.joinByCode(currentUser(3002L), invite.getInviteCode());

        assertThat(invite.getInviteCode()).matches("[A-Z0-9]{8,}");
        assertThat(result.getStatus()).isEqualTo("JOINED");
        verify(fixtures.teamAppService).ensureDirectMember(1001L, 2001L, 3002L, null, "MEMBER");
    }

    @Test
    void expiredOrUsageLimitedInviteCannotJoin() {
        Fixtures expired = fixtures();
        when(expired.teamInviteRepository.findByTokenHash(anyString())).thenReturn(invite(true, false, false));
        assertThatThrownBy(() -> expired.service.joinByToken(currentUser(3002L), "raw"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("expired");

        Fixtures full = fixtures();
        when(full.teamInviteRepository.findByTokenHash(anyString())).thenReturn(invite(false, true, false));
        assertThatThrownBy(() -> full.service.joinByToken(currentUser(3002L), "raw"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void inviteWithoutApprovalShouldJoinDirectlyAndRepeatJoinIsIdempotent() {
        Fixtures fixtures = fixtures();
        when(fixtures.permissionService.activeMember(1001L, 2001L, 3002L)).thenReturn(null);

        TeamVO.JoinResult result = fixtures.service.joinByToken(currentUser(3002L), "raw");

        assertThat(result.getStatus()).isEqualTo("JOINED");
        verify(fixtures.teamInviteRepository).consumeInviteQuota(any());
        verify(fixtures.teamAppService).ensureDirectMember(1001L, 2001L, 3002L, null, "MEMBER");

        when(fixtures.permissionService.activeMember(1001L, 2001L, 3002L)).thenReturn(member());
        TeamVO.JoinResult repeat = fixtures.service.joinByToken(currentUser(3002L), "raw");
        assertThat(repeat.getStatus()).isEqualTo("JOINED");
    }

    @Test
    void inviteWithApprovalShouldCreateJoinRequest() {
        Fixtures fixtures = fixtures();
        TeamVO.Invite approvalInvite = invite(false, false, true);
        when(fixtures.teamInviteRepository.findByTokenHash(anyString())).thenReturn(approvalInvite);
        when(fixtures.teamJoinRequestRepository.findPending(1001L, 2001L, 3002L)).thenReturn(null);

        TeamVO.JoinResult result = fixtures.service.joinByToken(currentUser(3002L), "raw");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(fixtures.teamJoinRequestRepository).createPending(1001L, 2001L, 3002L, 5001L, null);
        verify(fixtures.teamInviteRepository).consumeInviteQuota(approvalInvite);
    }

    @Test
    void repeatedPendingRequestShouldNotConsumeInviteAgain() {
        Fixtures fixtures = fixtures();
        TeamVO.Invite approvalInvite = invite(false, false, true);
        TeamVO.JoinRequest pending = joinRequest();
        when(fixtures.teamInviteRepository.findByTokenHash(anyString())).thenReturn(approvalInvite);
        when(fixtures.teamJoinRequestRepository.findPending(1001L, 2001L, 3002L)).thenReturn(pending);

        TeamVO.JoinResult result = fixtures.service.joinByToken(currentUser(3002L), "raw");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getJoinRequest()).isEqualTo(pending);
    }

    @Test
    void previewShouldExposeOnlyMinimalInviteInformation() {
        Fixtures fixtures = fixtures();
        when(fixtures.teamInviteRepository.findByTokenHash(anyString())).thenReturn(invite(false, false, true));

        TeamVO.InvitePreview preview = fixtures.service.previewByToken("raw");

        assertThat(preview.getTeamName()).isEqualTo("Core Team");
        assertThat(preview.getTeamType()).isEqualTo("GENERAL");
        assertThat(preview.getInviteStatus()).isEqualTo("ACTIVE");
        assertThat(preview.getNeedApproval()).isTrue();
    }

    private Fixtures fixtures() {
        TeamAppService teamAppService = teamService();
        TeamPermissionService permissionService = mock(TeamPermissionService.class);
        TeamInviteRepository teamInviteRepository = mock(TeamInviteRepository.class);
        TeamJoinRequestRepository teamJoinRequestRepository = mock(TeamJoinRequestRepository.class);
        when(permissionService.activeRole(1001L, 2001L, 3001L)).thenReturn("OWNER");
        when(permissionService.canInvite("OWNER")).thenReturn(true);
        TeamVO.Invite invite = invite(false, false, false);
        when(teamInviteRepository.createInvite(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), any(), any(), anyBoolean(), anyLong()))
                .thenAnswer(invocation -> {
                    invite.setInviteCode(invocation.getArgument(2));
                    return 5001L;
                });
        when(teamInviteRepository.findById(1001L, 2001L, 5001L)).thenReturn(invite);
        when(teamInviteRepository.findByTokenHash(anyString())).thenReturn(invite);
        when(teamInviteRepository.findByCode(1001L, "JOIN2026")).thenReturn(invite);
        when(teamInviteRepository.consumeInviteQuota(any())).thenReturn(true);
        when(teamJoinRequestRepository.createPending(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(6001L);
        when(teamJoinRequestRepository.findById(1001L, 2001L, 6001L)).thenReturn(joinRequest());
        TeamInviteService service = new TeamInviteService(
                teamAppService,
                permissionService,
                teamInviteRepository,
                teamJoinRequestRepository,
                (tenantId, userId, username, moduleName, actionName, operationType, resultStatus, detailMessage) -> {}
        );
        return new Fixtures(service, teamAppService, permissionService, teamInviteRepository, teamJoinRequestRepository);
    }

    private TeamAppService teamService() {
        TeamAppService service = mock(TeamAppService.class);
        when(service.requireTenantId(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(service.normalizeEnum(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Set.class), org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(service.trimToNull(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(service.queryTeam(1001L, 2001L, 3001L)).thenReturn(team("OWNER", "INVITE_ONLY"));
        when(service.queryTeam(1001L, 2001L, 3002L)).thenReturn(team("MEMBER", "INVITE_ONLY"));
        when(service.queryTeam(1001L, 2001L, null)).thenReturn(team(null, "INVITE_ONLY"));
        doNothing().when(service).ensureDirectMember(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        return service;
    }

    private static CurrentUser currentUser(Long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setCurrentTenantId(1001L);
        return user;
    }

    private static TeamVO.Team team(String role, String joinMode) {
        TeamVO.Team team = new TeamVO.Team();
        team.setId(2001L);
        team.setTenantId(1001L);
        team.setTeamName("Core Team");
        team.setTeamType("GENERAL");
        team.setVisibility("PRIVATE");
        team.setJoinMode(joinMode);
        team.setStatus("ACTIVE");
        team.setMyRole(role);
        return team;
    }

    private static TeamVO.Member member() {
        TeamVO.Member member = new TeamVO.Member();
        member.setId(1L);
        member.setUserId(3002L);
        member.setRole("MEMBER");
        member.setStatus("ACTIVE");
        return member;
    }

    private static TeamVO.Invite invite(boolean expired, boolean full, boolean approval) {
        TeamVO.Invite invite = new TeamVO.Invite();
        invite.setId(5001L);
        invite.setTenantId(1001L);
        invite.setTeamId(2001L);
        invite.setInviteCode("JOIN2026");
        invite.setRoleOnJoin("MEMBER");
        invite.setExpiresAt(expired ? LocalDateTime.now().minusMinutes(1) : LocalDateTime.now().plusDays(1));
        invite.setMaxUses(full ? 1 : 3);
        invite.setUsedCount(full ? 1 : 0);
        invite.setNeedApproval(approval);
        invite.setStatus("ACTIVE");
        return invite;
    }

    private static TeamVO.JoinRequest joinRequest() {
        TeamVO.JoinRequest request = new TeamVO.JoinRequest();
        request.setId(6001L);
        request.setTenantId(1001L);
        request.setTeamId(2001L);
        request.setUserId(3002L);
        request.setStatus("PENDING");
        return request;
    }

    private record Fixtures(
            TeamInviteService service,
            TeamAppService teamAppService,
            TeamPermissionService permissionService,
            TeamInviteRepository teamInviteRepository,
            TeamJoinRequestRepository teamJoinRequestRepository
    ) {}
}
