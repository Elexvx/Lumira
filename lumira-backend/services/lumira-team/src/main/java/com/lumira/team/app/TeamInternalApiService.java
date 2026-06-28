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
    public TeamSummaryDTO getTeam(Long teamId) {
        List<TeamSummaryDTO> teams = jdbcTemplate.query(
                """
                        select id, team_code as teamCode, team_name as teamName,
                               team_type as teamType, visibility, owner_user_id as ownerUserId, status
                        from team
                        where id = ?
                          and deleted = 0
                        limit 1
                """,
                new BeanPropertyRowMapper<>(TeamSummaryDTO.class),
                teamId
        );
        return teams.isEmpty() ? null : teams.get(0);
    }

    @Override
    public List<TeamMemberDTO> listActiveMembers(Long teamId) {
        return jdbcTemplate.query(
                """
                        select id, team_id as teamId, user_id as userId,
                               role, status, extra_values_json as extraValuesJson, joined_at as joinedAt
                        from team_member
                        where team_id = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                        order by id asc
                """,
                new BeanPropertyRowMapper<>(TeamMemberDTO.class),
                teamId
        );
    }

    @Override
    public TeamMemberDTO requireActiveMember(Long teamId, Long userId) {
        TeamMemberDTO member = activeMember(teamId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "Team membership required", "Team membership required");
        }
        return member;
    }

    @Override
    public boolean isTeamOwner(Long teamId, Long userId) {
        return TeamPermissionService.OWNER.equals(permissionService.activeRole(teamId, userId));
    }

    @Override
    public boolean isTeamAdmin(Long teamId, Long userId) {
        String role = permissionService.activeRole(teamId, userId);
        return TeamPermissionService.OWNER.equals(role) || TeamPermissionService.ADMIN.equals(role);
    }

    @Override
    public boolean isTeamManager(Long teamId, Long userId) {
        String role = permissionService.activeRole(teamId, userId);
        return TeamPermissionService.OWNER.equals(role)
                || TeamPermissionService.ADMIN.equals(role)
                || TeamPermissionService.MANAGER.equals(role);
    }

    private TeamMemberDTO activeMember(Long teamId, Long userId) {
        List<TeamMemberDTO> members = jdbcTemplate.query(
                """
                        select id, team_id as teamId, user_id as userId,
                               role, status, extra_values_json as extraValuesJson, joined_at as joinedAt
                        from team_member
                        where team_id = ?
                          and user_id = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                        limit 1
                """,
                new BeanPropertyRowMapper<>(TeamMemberDTO.class),
                teamId,
                userId
        );
        return members.isEmpty() ? null : members.get(0);
    }
}
