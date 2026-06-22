package com.lumira.team.app;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import com.lumira.team.infrastructure.persistence.RowMapper;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamInviteServiceTest {
    @Test
    void createInviteShouldReturnRawTokenAndPersistOnlyHash() {
        RecordingQueries queries = new RecordingQueries();
        TeamAppService teamService = teamService();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeRole(1001L, 2001L, 3001L)).thenReturn("OWNER");
        when(permission.canInvite("OWNER")).thenReturn(true);
        TeamInviteService service = service(queries, teamService, permission);
        TeamDTO.InviteCreateRequest request = new TeamDTO.InviteCreateRequest();
        request.setInviteCode("JOIN2026");

        TeamVO.Invite invite = service.createInvite(currentUser(3001L), 2001L, request);

        assertThat(invite.getRawToken()).isNotBlank();
        assertThat(invite.getInviteUrl()).contains(invite.getRawToken());
        assertThat(queries.persistedTokenHash).hasSize(64);
        assertThat(queries.persistedTokenHash).isNotEqualTo(invite.getRawToken());
    }

    @Test
    void blankInviteCodeShouldBeGeneratedAndJoinable() {
        RecordingQueries queries = new RecordingQueries();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeRole(1001L, 2001L, 3001L)).thenReturn("OWNER");
        when(permission.canInvite("OWNER")).thenReturn(true);
        when(permission.activeMember(1001L, 2001L, 3002L)).thenReturn(null);
        TeamInviteService service = service(queries, teamService(), permission);
        TeamDTO.InviteCreateRequest request = new TeamDTO.InviteCreateRequest();

        TeamVO.Invite invite = service.createInvite(currentUser(3001L), 2001L, request);
        TeamVO.JoinResult result = service.joinByCode(currentUser(3002L), invite.getInviteCode());

        assertThat(invite.getInviteCode()).matches("[A-Z0-9]{8,}");
        assertThat(result.getStatus()).isEqualTo("JOINED");
    }

    @Test
    void expiredOrUsageLimitedInviteCannotJoin() {
        RecordingQueries expired = new RecordingQueries();
        expired.invite = invite(true, false, false);
        TeamInviteService expiredService = service(expired, teamService(), mock(TeamPermissionService.class));
        assertThatThrownBy(() -> expiredService.joinByToken(currentUser(3002L), "raw"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("expired");

        RecordingQueries full = new RecordingQueries();
        full.invite = invite(false, true, false);
        TeamInviteService fullService = service(full, teamService(), mock(TeamPermissionService.class));
        assertThatThrownBy(() -> fullService.joinByToken(currentUser(3002L), "raw"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void inviteWithoutApprovalShouldJoinDirectlyAndRepeatJoinIsIdempotent() {
        RecordingQueries queries = new RecordingQueries();
        TeamAppService teamService = teamService();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeMember(1001L, 2001L, 3002L)).thenReturn(null);
        TeamInviteService service = service(queries, teamService, permission);

        TeamVO.JoinResult result = service.joinByToken(currentUser(3002L), "raw");

        assertThat(result.getStatus()).isEqualTo("JOINED");
        assertThat(queries.consumeInviteCalled).isTrue();
        verify(teamService).ensureDirectMember(1001L, 2001L, 3002L, null, "MEMBER");

        when(permission.activeMember(1001L, 2001L, 3002L)).thenReturn(member());
        TeamVO.JoinResult repeat = service.joinByToken(currentUser(3002L), "raw");
        assertThat(repeat.getStatus()).isEqualTo("JOINED");
    }

    @Test
    void inviteWithApprovalShouldCreateJoinRequest() {
        RecordingQueries queries = new RecordingQueries();
        queries.invite = invite(false, false, true);
        TeamInviteService service = service(queries, teamService(), mock(TeamPermissionService.class));

        TeamVO.JoinResult result = service.joinByToken(currentUser(3002L), "raw");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(queries.joinRequestInsertCalled).isTrue();
        assertThat(queries.consumeInviteCalled).isTrue();
    }

    @Test
    void repeatedPendingRequestShouldNotConsumeInviteAgain() {
        RecordingQueries queries = new RecordingQueries();
        queries.invite = invite(false, false, true);
        queries.pendingRequestAlreadyExists = true;
        TeamInviteService service = service(queries, teamService(), mock(TeamPermissionService.class));

        TeamVO.JoinResult result = service.joinByToken(currentUser(3002L), "raw");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(queries.consumeInviteCalled).isFalse();
        assertThat(queries.joinRequestInsertCalled).isFalse();
    }

    @Test
    void previewShouldExposeOnlyMinimalInviteInformation() {
        RecordingQueries queries = new RecordingQueries();
        queries.invite = invite(false, false, true);
        TeamInviteService service = service(queries, teamService(), mock(TeamPermissionService.class));

        TeamVO.InvitePreview preview = service.previewByToken("raw");

        assertThat(preview.getTeamName()).isEqualTo("Core Team");
        assertThat(preview.getTeamType()).isEqualTo("GENERAL");
        assertThat(preview.getInviteStatus()).isEqualTo("ACTIVE");
        assertThat(preview.getNeedApproval()).isTrue();
    }

    private TeamInviteService service(RecordingQueries queries, TeamAppService teamService, TeamPermissionService permission) {
        return new TeamInviteService(
                queries,
                teamService,
                permission,
                (tenantId, userId, username, moduleName, actionName, operationType, resultStatus, detailMessage) -> {}
        );
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

    private static final class RecordingQueries extends MyBatisQueryOperations {
        private TeamVO.Invite invite = invite(false, false, false);
        private String persistedTokenHash;
        private String persistedInviteCode;
        private boolean consumeInviteCalled;
        private boolean joinRequestInsertCalled;
        private boolean pendingRequestAlreadyExists;

        @Override
        public boolean exists(String sql, Object... args) {
            return false;
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("insert into team_invite")) {
                persistedInviteCode = String.valueOf(args[2]);
                persistedTokenHash = String.valueOf(args[3]);
                invite.setInviteCode(persistedInviteCode);
            }
            if (sql.contains("used_count = used_count + 1")) {
                consumeInviteCalled = true;
            }
            if (sql.contains("insert into team_join_request")) {
                joinRequestInsertCalled = true;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                return requiredType.cast(joinRequestInsertCalled ? 6001L : 5001L);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from team_invite")) {
                return cast(List.of(invite));
            }
            if (sql.contains("from team_join_request")) {
                if (!joinRequestInsertCalled && !pendingRequestAlreadyExists) {
                    return List.of();
                }
                TeamVO.JoinRequest request = new TeamVO.JoinRequest();
                request.setId(6001L);
                request.setTenantId(1001L);
                request.setTeamId(2001L);
                request.setUserId(3002L);
                request.setStatus("PENDING");
                return cast(List.of(request));
            }
            return List.of();
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> cast(List<?> value) {
            return (List<T>) new ArrayList<>(value);
        }
    }
}
