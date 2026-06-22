package com.lumira.team.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import com.lumira.team.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TeamInternalApiService implements TeamInternalApi {
    private final MyBatisQueryOperations jdbcTemplate;
    private final TeamPermissionService permissionService;

    public TeamInternalApiService(MyBatisQueryOperations jdbcTemplate, TeamPermissionService permissionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
    }

    @Override
    public TeamSummaryDTO getTeam(Long tenantId, Long teamId) {
        List<TeamSummaryDTO> teams = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, team_code as teamCode, team_name as teamName,
                               team_type as teamType, visibility, owner_user_id as ownerUserId, status
                        from team
                        where tenant_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(TeamSummaryDTO.class),
                tenantId,
                teamId
        );
        return teams.isEmpty() ? null : teams.get(0);
    }

    @Override
    public List<TeamMemberDTO> listActiveMembers(Long tenantId, Long teamId) {
        return jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, team_id as teamId, user_id as userId,
                               role, status, joined_at as joinedAt
                        from team_member
                        where tenant_id = ?
                          and team_id = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                        order by id asc
                        """,
                new BeanPropertyRowMapper<>(TeamMemberDTO.class),
                tenantId,
                teamId
        );
    }

    @Override
    public TeamMemberDTO requireActiveMember(Long tenantId, Long teamId, Long userId) {
        TeamMemberDTO member = activeMember(tenantId, teamId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "Team membership required", "Team membership required");
        }
        return member;
    }

    @Override
    public boolean isTeamOwner(Long tenantId, Long teamId, Long userId) {
        return TeamPermissionService.OWNER.equals(permissionService.activeRole(tenantId, teamId, userId));
    }

    @Override
    public boolean isTeamAdmin(Long tenantId, Long teamId, Long userId) {
        String role = permissionService.activeRole(tenantId, teamId, userId);
        return TeamPermissionService.OWNER.equals(role) || TeamPermissionService.ADMIN.equals(role);
    }

    @Override
    public boolean isTeamManager(Long tenantId, Long teamId, Long userId) {
        String role = permissionService.activeRole(tenantId, teamId, userId);
        return TeamPermissionService.OWNER.equals(role)
                || TeamPermissionService.ADMIN.equals(role)
                || TeamPermissionService.MANAGER.equals(role);
    }

    private TeamMemberDTO activeMember(Long tenantId, Long teamId, Long userId) {
        List<TeamMemberDTO> members = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, team_id as teamId, user_id as userId,
                               role, status, joined_at as joinedAt
                        from team_member
                        where tenant_id = ?
                          and team_id = ?
                          and user_id = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(TeamMemberDTO.class),
                tenantId,
                teamId,
                userId
        );
        return members.isEmpty() ? null : members.get(0);
    }
}
