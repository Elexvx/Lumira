package com.lumira.saas.modules.team.app;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.team.dto.TeamDTO;
import com.lumira.saas.modules.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamAppServiceTest {
    @Test
    void createTeamShouldInsertOwnerMember() {
        RecordingQueries queries = new RecordingQueries();
        TeamAppService service = service(queries, mockPermission("OWNER"));
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");

        TeamVO.Team team = service.createTeam(currentUser(3001L), request);

        assertThat(team.getId()).isEqualTo(2001L);
        assertThat(queries.teamInsertCalled).isTrue();
        assertThat(queries.ownerMemberInsertCalled).isTrue();
        assertThat(queries.memberInsertRole).isEqualTo("OWNER");
    }

    @Test
    void myTeamsShouldUseMembershipScope() {
        RecordingQueries queries = new RecordingQueries();
        TeamAppService service = service(queries, mockPermission("MEMBER"));

        List<TeamVO.Team> teams = service.myTeams(currentUser(3001L));

        assertThat(teams).hasSize(1);
        assertThat(queries.lastQuerySql).contains("join team_member");
        assertThat(teams.get(0).getMyRole()).isEqualTo("MEMBER");
    }

    @Test
    void ownerCanUpdateButMemberCannot() {
        TeamDTO.TeamUpdateRequest request = new TeamDTO.TeamUpdateRequest();
        request.setTeamName("Updated");
        TeamAppService ownerService = service(new RecordingQueries(), mockPermission("OWNER"));
        assertThat(ownerService.updateTeam(currentUser(3001L), 2001L, request).getTeamName()).isEqualTo("Core Team");

        TeamAppService memberService = service(new RecordingQueries(), mockPermission("MEMBER"));
        assertThatThrownBy(() -> memberService.updateTeam(currentUser(3001L), 2001L, request))
                .isInstanceOf(BizException.class);
    }

    @Test
    void ownerCannotLeaveDirectly() {
        TeamPermissionService permission = mockPermission("OWNER");
        TeamVO.Member owner = member(1L, 3001L, "OWNER");
        when(permission.activeMember(1001L, 2001L, 3001L)).thenReturn(owner);
        TeamAppService service = service(new RecordingQueries(), permission);

        assertThatThrownBy(() -> service.leaveTeam(currentUser(3001L), 2001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Owner must transfer");
    }

    @Test
    void transferOwnerShouldDemotePreviousOwner() {
        RecordingQueries queries = new RecordingQueries();
        TeamPermissionService permission = mockPermission("OWNER");
        when(permission.memberById(1001L, 2001L, 2L)).thenReturn(member(2L, 3002L, "ADMIN"));
        TeamAppService service = service(queries, permission);
        TeamDTO.TransferOwnerRequest request = new TeamDTO.TransferOwnerRequest();
        request.setMemberId(2L);
        request.setPreviousOwnerRole("MEMBER");

        service.transferOwner(currentUser(3001L), 2001L, request);

        assertThat(queries.previousOwnerRole).isEqualTo("MEMBER");
        assertThat(queries.newOwnerUpdated).isTrue();
    }

    private TeamAppService service(RecordingQueries queries, TeamPermissionService permissionService) {
        return new TeamAppService(
                queries,
                permissionService,
                new OperationAuditService(null) {
                    @Override
                    public void log(Long tenantId, Long userId, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                }
        );
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

    private static CurrentUser currentUser(Long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setUsername("user" + userId);
        user.setCurrentTenantId(1001L);
        return user;
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

    private static final class RecordingQueries extends MyBatisQueryOperations {
        private boolean teamInsertCalled;
        private boolean ownerMemberInsertCalled;
        private String memberInsertRole;
        private String lastQuerySql;
        private String previousOwnerRole;
        private boolean newOwnerUpdated;

        @Override
        public boolean exists(String sql, Object... args) {
            return false;
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("insert into team (")) {
                teamInsertCalled = true;
            }
            if (sql.contains("insert into team_member")) {
                ownerMemberInsertCalled = true;
                memberInsertRole = sql.contains("'OWNER'") ? "OWNER" : String.valueOf(args[3]);
            }
            if (sql.contains("set role = ?") && args.length > 0) {
                previousOwnerRole = String.valueOf(args[0]);
            }
            if (sql.contains("set role = 'OWNER'")) {
                newOwnerUpdated = true;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                return requiredType.cast(2001L);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            lastQuerySql = sql;
            return cast(List.of(team()));
        }

        private TeamVO.Team team() {
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
            team.setCreatedAt(LocalDateTime.now());
            return team;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> cast(List<?> value) {
            return (List<T>) new ArrayList<>(value);
        }
    }
}
