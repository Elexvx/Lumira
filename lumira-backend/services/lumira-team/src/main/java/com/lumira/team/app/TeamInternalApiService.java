package com.lumira.team.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import com.lumira.team.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service("teamInternalApi")
@Primary
public class TeamInternalApiService implements TeamInternalApi {
    private final MyBatisQueryOperations jdbcTemplate;
    private final TeamPermissionService permissionService;
    private final ObjectProvider<SystemInternalApi> systemInternalApi;

    public TeamInternalApiService(
            MyBatisQueryOperations jdbcTemplate,
            TeamPermissionService permissionService,
            ObjectProvider<SystemInternalApi> systemInternalApi
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.systemInternalApi = systemInternalApi;
    }

    @Override
    public TeamSummaryDTO getTeam(Long requesterUserId, String requesterUserUuid, Long teamId) {
        requireInternalServicePrincipal();
        requireTrustedUser(requesterUserId, requesterUserUuid, "Requester");
        requirePositiveId(teamId, "Team id is required");
        requireActiveMember(teamId, requesterUserId, requesterUserUuid);
        List<TeamSummaryDTO> teams = jdbcTemplate.query(
                """
                        select id, team_code as teamCode, team_name as teamName,
                               team_type as teamType, visibility, owner_user_id as ownerUserId, owner_user_uuid as ownerUserUuid, status
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
    public List<TeamMemberDTO> listActiveMembers(Long requesterUserId, String requesterUserUuid, Long teamId) {
        requireInternalServicePrincipal();
        requireTrustedUser(requesterUserId, requesterUserUuid, "Requester");
        requirePositiveId(teamId, "Team id is required");
        requireActiveMember(teamId, requesterUserId, requesterUserUuid);
        return jdbcTemplate.query(
                """
                        select id, team_id as teamId, user_id as userId, user_uuid as userUuid,
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
    public TeamMemberDTO requireActiveMember(Long teamId, Long userId, String userUuid) {
        requireInternalServicePrincipal();
        requirePositiveId(teamId, "Team id is required");
        requireTrustedUser(userId, userUuid, "User");
        TeamMemberDTO member = activeMember(teamId, userId, userUuid);
        if (member == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "Team membership required", "Team membership required");
        }
        return member;
    }

    @Override
    public boolean isTeamOwner(Long teamId, Long userId, String userUuid) {
        requireInternalServicePrincipal();
        requirePositiveId(teamId, "Team id is required");
        requireTrustedUser(userId, userUuid, "User");
        return TeamPermissionService.OWNER.equals(activeRole(teamId, userId, userUuid));
    }

    @Override
    public boolean isTeamAdmin(Long teamId, Long userId, String userUuid) {
        requireInternalServicePrincipal();
        requirePositiveId(teamId, "Team id is required");
        requireTrustedUser(userId, userUuid, "User");
        String role = activeRole(teamId, userId, userUuid);
        return TeamPermissionService.OWNER.equals(role) || TeamPermissionService.ADMIN.equals(role);
    }

    @Override
    public boolean isTeamManager(Long teamId, Long userId, String userUuid) {
        requireInternalServicePrincipal();
        requirePositiveId(teamId, "Team id is required");
        requireTrustedUser(userId, userUuid, "User");
        String role = activeRole(teamId, userId, userUuid);
        return TeamPermissionService.OWNER.equals(role)
                || TeamPermissionService.ADMIN.equals(role)
                || TeamPermissionService.MANAGER.equals(role);
    }

    private void requireInternalServicePrincipal() {
        if (!AuthenticationTrustSupport.isInternalServiceAuthentication(SecurityContextHolder.getContext().getAuthentication())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Internal service token is required", "Internal service token is required");
        }
    }

    private void requireTrustedUser(Long userId, String userUuid, String label) {
        requirePositiveId(userId, label + " user id is required");
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, label + " user uuid is required", label + " user uuid is required");
        }
        SystemInternalApi internalApi = systemInternalApi == null ? null : systemInternalApi.getIfAvailable();
        if (internalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable", "Trusted user resolver is unavailable");
        }
        SystemUserSnapshotDTO snapshot = internalApi.findUserIdentityById(userId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, label + " user does not exist", label + " user does not exist");
        }
        if (!StringUtils.hasText(snapshot.userUuid()) || !snapshot.userUuid().trim().equals(userUuid.trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, label + " user identity mismatch", label + " user identity mismatch");
        }
        if (!StringUtils.hasText(snapshot.status()) || !"ENABLED".equalsIgnoreCase(snapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, label + " user is disabled", label + " user is disabled");
        }
    }

    private String activeRole(Long teamId, Long userId, String userUuid) {
        TeamMemberDTO member = activeMember(teamId, userId, userUuid);
        return member == null ? null : member.getRole();
    }

    private TeamMemberDTO activeMember(Long teamId, Long userId, String userUuid) {
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(userId, "User id is required");
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User user uuid is required", "User user uuid is required");
        }
        List<TeamMemberDTO> members = jdbcTemplate.query(
                """
                        select m.id, m.team_id as teamId, m.user_id as userId, m.user_uuid as userUuid,
                               m.role, m.status, m.extra_values_json as extraValuesJson, m.joined_at as joinedAt
                        from team_member m
                        join sys_user u on u.id = m.user_id
                          and u.uuid = ?
                          and u.deleted = 0
                          and u.status = 'ENABLED'
                        where m.team_id = ?
                          and m.user_id = ?
                          and m.user_uuid = ?
                          and m.status = 'ACTIVE'
                          and m.deleted = 0
                        limit 1
                """,
                new BeanPropertyRowMapper<>(TeamMemberDTO.class),
                userUuid.trim(),
                teamId,
                userId,
                userUuid.trim()
        );
        return members.isEmpty() ? null : members.get(0);
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message, message);
        }
    }
}
