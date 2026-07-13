package com.lumira.team.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service("teamInternalApi")
@Primary
public class TeamInternalApiService implements TeamInternalApi {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final TeamPermissionService permissionService;
    private final ObjectProvider<SystemInternalApi> systemInternalApi;

    public TeamInternalApiService(
            TeamRepository teamRepository,
            TeamMemberRepository memberRepository,
            TeamPermissionService permissionService,
            ObjectProvider<SystemInternalApi> systemInternalApi
    ) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.permissionService = permissionService;
        this.systemInternalApi = systemInternalApi;
    }

    @Override
    public TeamSummaryDTO getTeam(Long requesterUserId, String requesterUserUuid, Long teamId) {
        requireInternalServicePrincipal();
        requireTrustedUser(requesterUserId, requesterUserUuid, "Requester");
        requirePositiveId(teamId, "Team id is required");
        permissionService.requireTeamMember(teamId, requesterUserId, requesterUserUuid);
        return toSummary(teamRepository.findTeam(teamId, requesterUserId, requesterUserUuid));
    }

    @Override
    public List<TeamMemberDTO> listActiveMembers(Long requesterUserId, String requesterUserUuid, Long teamId) {
        requireInternalServicePrincipal();
        requireTrustedUser(requesterUserId, requesterUserUuid, "Requester");
        requirePositiveId(teamId, "Team id is required");
        permissionService.requireTeamMember(teamId, requesterUserId, requesterUserUuid);
        return memberRepository.listMembers(teamId).stream()
                .filter(member -> "ACTIVE".equals(member.getStatus()))
                .map(this::toMember)
                .toList();
    }

    @Override
    public TeamMemberDTO requireActiveMember(Long teamId, Long userId, String userUuid) {
        requireInternalServicePrincipal();
        requirePositiveId(teamId, "Team id is required");
        requireTrustedUser(userId, userUuid, "User");
        TeamMemberDTO member = toMember(permissionService.activeMember(teamId, userId, userUuid));
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
        TeamMemberDTO member = toMember(permissionService.activeMember(teamId, userId, userUuid));
        return member == null ? null : member.getRole();
    }

    private TeamSummaryDTO toSummary(TeamVO.Team team) {
        if (team == null) return null;
        TeamSummaryDTO dto = new TeamSummaryDTO();
        dto.setId(team.getId()); dto.setTeamCode(team.getTeamCode()); dto.setTeamName(team.getTeamName());
        dto.setTeamType(team.getTeamType()); dto.setVisibility(team.getVisibility());
        dto.setOwnerUserId(team.getOwnerUserId()); dto.setOwnerUserUuid(team.getOwnerUserUuid()); dto.setStatus(team.getStatus());
        return dto;
    }

    private TeamMemberDTO toMember(TeamVO.Member member) {
        if (member == null) return null;
        TeamMemberDTO dto = new TeamMemberDTO();
        dto.setId(member.getId()); dto.setTeamId(member.getTeamId()); dto.setUserId(member.getUserId());
        dto.setUserUuid(member.getUserUuid()); dto.setRole(member.getRole()); dto.setStatus(member.getStatus());
        dto.setExtraValuesJson(member.getExtraValuesJson()); dto.setJoinedAt(member.getJoinedAt());
        return dto;
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message, message);
        }
    }
}
