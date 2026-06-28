package com.lumira.team.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TeamAppService {
    private static final String TEAM_TYPE_DICT_CODE = "team_type";
    private static final String TEAM_VISIBILITY_DICT_CODE = "team_visibility";
    private static final String TEAM_JOIN_MODE_DICT_CODE = "team_join_mode";
    private static final Set<String> TEAM_TYPES = Set.of("GENERAL", "DEV", "COMPETITION", "CLUB", "OTHER");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "PUBLIC");
    private static final Set<String> JOIN_MODES = Set.of("INVITE_ONLY", "APPLY", "OPEN");
    private static final Set<String> MEMBER_ROLES = Set.of("OWNER", "ADMIN", "MANAGER", "MEMBER");

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final TeamJoinRequestRepository teamJoinRequestRepository;
    private final TeamPermissionService permissionService;
    private final TeamAuditPort auditPort;

    public TeamAppService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamInviteRepository teamInviteRepository,
            TeamJoinRequestRepository teamJoinRequestRepository,
            TeamPermissionService permissionService,
            TeamAuditPort auditPort
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInviteRepository = teamInviteRepository;
        this.teamJoinRequestRepository = teamJoinRequestRepository;
        this.permissionService = permissionService;
        this.auditPort = auditPort;
    }

    @Transactional
    public TeamVO.Team createTeam(CurrentUser currentUser, TeamDTO.TeamCreateRequest request) {
        Long userId = requireUserId(currentUser);
        TeamDTO.TeamCreateRequest normalizedRequest = normalizeCreateRequest(request);
        Long teamId = teamRepository.createTeam(teamRepository.nextTeamCode(), userId, normalizedRequest);
        teamMemberRepository.addOwner(teamId, userId);
        addDraftMembers(teamId, normalizedRequest.getInitialMembers());
        audit(currentUser, "team", "create", "CREATE", "Created team " + normalizedRequest.getTeamName());
        return getTeam(currentUser, teamId);
    }

    public List<TeamVO.Team> myTeams(CurrentUser currentUser) {
        requireUserId(currentUser);
        return teamRepository.listMyTeams(currentUser.getUserId());
    }

    public List<TeamVO.Team> listTeamsForAdmin(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        return teamRepository.listTeamsForAdmin(userId);
    }

    public TeamVO.Team getTeam(CurrentUser currentUser, Long teamId) {
        requireUserId(currentUser);
        TeamVO.Team team = queryTeam(teamId, currentUser.getUserId());
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        if ("PRIVATE".equals(team.getVisibility()) && team.getMyRole() == null) {
            throw biz(ErrorCode.FORBIDDEN, "Private team details require membership");
        }
        return team;
    }

    @Transactional
    public TeamVO.Team updateTeam(CurrentUser currentUser, Long teamId, TeamDTO.TeamUpdateRequest request) {
        requireUserId(currentUser);
        String role = permissionService.activeRole(teamId, currentUser.getUserId());
        if (!permissionService.canUpdateTeam(role)) {
            throw biz(ErrorCode.FORBIDDEN, "Team update requires owner, admin, or manager");
        }
        updateTeamProfile(currentUser, teamId, request);
        audit(currentUser, "team", "update", "UPDATE", "Updated team " + teamId);
        return getTeam(currentUser, teamId);
    }

    @Transactional
    public TeamVO.Team updateTeamForAdmin(CurrentUser currentUser, Long teamId, TeamDTO.TeamUpdateRequest request) {
        requireUserId(currentUser);
        updateTeamProfile(currentUser, teamId, request);
        audit(currentUser, "team", "adminUpdate", "UPDATE", "Admin updated team " + teamId);
        TeamVO.Team team = queryTeam(teamId, currentUser.getUserId());
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        return team;
    }

    @Transactional
    public boolean deleteTeam(CurrentUser currentUser, Long teamId) {
        requireUserId(currentUser);
        permissionService.requireTeamOwner(teamId, currentUser.getUserId());
        deleteTeamAggregate(currentUser, teamId, "delete", "Deleted team ");
        return true;
    }

    @Transactional
    public boolean deleteTeamForAdmin(CurrentUser currentUser, Long teamId) {
        requireUserId(currentUser);
        deleteTeamAggregate(currentUser, teamId, "adminDelete", "Admin deleted team ");
        return true;
    }

    public List<TeamVO.Member> listMembers(CurrentUser currentUser, Long teamId) {
        requireUserId(currentUser);
        permissionService.requireTeamMember(teamId, currentUser.getUserId());
        return teamMemberRepository.listMembers(teamId);
    }

    @Transactional
    public TeamVO.Member addMember(CurrentUser currentUser, Long teamId, TeamDTO.MemberCreateRequest request) {
        requireUserId(currentUser);
        String actorRole = permissionService.activeRole(teamId, currentUser.getUserId());
        if (!permissionService.canUpdateTeam(actorRole)) {
            throw biz(ErrorCode.FORBIDDEN, "Team member creation requires owner, admin, or manager");
        }
        Long memberId = teamMemberRepository.addDraftMember(teamId, normalizeDraftMember(request));
        teamMemberRepository.refreshMemberCount(teamId);
        audit(currentUser, "team", "addMember", "CREATE", "Added draft team member " + memberId);
        return requireMember(teamId, memberId);
    }

    @Transactional
    public TeamVO.Member updateMemberRole(CurrentUser currentUser, Long teamId, Long memberId, TeamDTO.MemberRoleRequest request) {
        requireUserId(currentUser);
        String actorRole = permissionService.activeRole(teamId, currentUser.getUserId());
        if (!TeamPermissionService.OWNER.equals(actorRole) && !TeamPermissionService.ADMIN.equals(actorRole)) {
            throw biz(ErrorCode.FORBIDDEN, "Member role updates require owner or admin");
        }
        TeamVO.Member target = requireMember(teamId, memberId);
        String newRole = normalizeEnum(request.getRole(), null, MEMBER_ROLES, "Invalid team member role");
        if (TeamPermissionService.OWNER.equals(newRole)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Use owner transfer to assign OWNER");
        }
        if (TeamPermissionService.OWNER.equals(target.getRole())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Cannot downgrade the team owner directly");
        }
        teamMemberRepository.updateMemberRole(teamId, memberId, newRole);
        return requireMember(teamId, memberId);
    }

    @Transactional
    public boolean removeMember(CurrentUser currentUser, Long teamId, Long memberId) {
        requireUserId(currentUser);
        TeamVO.Member actor = permissionService.activeMember(teamId, currentUser.getUserId());
        TeamVO.Member target = requireMember(teamId, memberId);
        boolean self = Objects.equals(target.getUserId(), currentUser.getUserId());
        if (!permissionService.canRemoveMember(actor == null ? null : actor.getRole(), target.getRole(), self)) {
            throw biz(ErrorCode.FORBIDDEN, "Cannot remove this team member");
        }
        teamMemberRepository.removeMember(teamId, memberId);
        teamMemberRepository.refreshMemberCount(teamId);
        return true;
    }

    @Transactional
    public boolean leaveTeam(CurrentUser currentUser, Long teamId) {
        requireUserId(currentUser);
        TeamVO.Member member = permissionService.activeMember(teamId, currentUser.getUserId());
        if (member == null) {
            return true;
        }
        if (TeamPermissionService.OWNER.equals(member.getRole())) {
            throw biz(ErrorCode.BIZ_ERROR, "Owner must transfer ownership or disband the team before leaving");
        }
        return removeMember(currentUser, teamId, member.getId());
    }

    @Transactional
    public TeamVO.Team transferOwner(CurrentUser currentUser, Long teamId, TeamDTO.TransferOwnerRequest request) {
        requireUserId(currentUser);
        permissionService.requireTeamOwner(teamId, currentUser.getUserId());
        TeamVO.Member target = requireMember(teamId, request.getMemberId());
        if (!"ACTIVE".equals(target.getStatus())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "New owner must be an active member");
        }
        String previousRole = normalizeEnum(request.getPreviousOwnerRole(), "ADMIN", Set.of("ADMIN", "MANAGER", "MEMBER"), "Invalid previous owner role");
        teamMemberRepository.transferOwner(teamId, currentUser.getUserId(), previousRole, target.getId());
        teamRepository.transferOwner(teamId, target.getUserId(), currentUser.getUserId());
        audit(currentUser, "team", "transferOwner", "UPDATE", "Transferred team owner " + teamId);
        return getTeam(currentUser, teamId);
    }

    TeamVO.Team queryTeam(Long teamId, Long currentUserId) {
        return teamRepository.findTeam(teamId, currentUserId);
    }

    TeamVO.Member requireMember(Long teamId, Long memberId) {
        TeamVO.Member member = teamMemberRepository.findMemberById(teamId, memberId);
        if (member == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team member not found");
        }
        return member;
    }

    void ensureDirectMember(Long teamId, Long userId, Long invitedBy, String role) {
        teamMemberRepository.ensureDirectMember(teamId, userId, invitedBy, role);
        teamMemberRepository.refreshMemberCount(teamId);
    }

    void refreshMemberCount(Long teamId) {
        teamMemberRepository.refreshMemberCount(teamId);
    }

    Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null || currentUser.getUserId() <= 0) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    String normalizeEnum(String value, String defaultValue, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (normalized == null || !allowed.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String normalizeDictValue(String value, String defaultValue, String dictCode, Set<String> fallbackAllowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        Set<String> allowed = teamRepository.loadEnabledDictValues(dictCode);
        if (allowed.isEmpty()) {
            allowed = fallbackAllowed;
        }
        if (normalized == null || !allowed.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private TeamDTO.TeamCreateRequest normalizeCreateRequest(TeamDTO.TeamCreateRequest request) {
        TeamDTO.TeamCreateRequest normalized = new TeamDTO.TeamCreateRequest();
        normalized.setTeamName(trimRequired(request.getTeamName(), "Team name is required"));
        normalized.setTeamType(normalizeDictValue(request.getTeamType(), "GENERAL", TEAM_TYPE_DICT_CODE, TEAM_TYPES, "Invalid team type"));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setVisibility(normalizeDictValue(request.getVisibility(), "PRIVATE", TEAM_VISIBILITY_DICT_CODE, VISIBILITIES, "Invalid team visibility"));
        normalized.setJoinMode(normalizeDictValue(request.getJoinMode(), "INVITE_ONLY", TEAM_JOIN_MODE_DICT_CODE, JOIN_MODES, "Invalid join mode"));
        if (request.getInitialMembers() != null) {
            normalized.setInitialMembers(request.getInitialMembers().stream()
                    .map(this::normalizeDraftMember)
                    .toList());
        }
        return normalized;
    }

    private TeamDTO.TeamUpdateRequest normalizeUpdateRequest(TeamDTO.TeamUpdateRequest request) {
        TeamDTO.TeamUpdateRequest normalized = new TeamDTO.TeamUpdateRequest();
        normalized.setTeamName(trimRequired(request.getTeamName(), "Team name is required"));
        normalized.setTeamType(normalizeDictValue(request.getTeamType(), "GENERAL", TEAM_TYPE_DICT_CODE, TEAM_TYPES, "Invalid team type"));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setVisibility(normalizeDictValue(request.getVisibility(), "PRIVATE", TEAM_VISIBILITY_DICT_CODE, VISIBILITIES, "Invalid team visibility"));
        normalized.setJoinMode(normalizeDictValue(request.getJoinMode(), "INVITE_ONLY", TEAM_JOIN_MODE_DICT_CODE, JOIN_MODES, "Invalid join mode"));
        return normalized;
    }

    private TeamDTO.DraftMemberRequest normalizeDraftMember(TeamDTO.DraftMemberRequest request) {
        TeamDTO.DraftMemberRequest normalized = new TeamDTO.DraftMemberRequest();
        normalized.setMemberName(trimRequired(request.getMemberName(), "Member name is required"));
        normalized.setEmployeeNo(trimToNull(request.getEmployeeNo()));
        normalized.setDepartmentName(trimToNull(request.getDepartmentName()));
        normalized.setRole(normalizeEnum(request.getRole(), "MEMBER", MEMBER_ROLES, "Invalid team member role"));
        if ("OWNER".equals(normalized.getRole())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Draft members cannot be owner");
        }
        normalized.setRemark(trimToNull(request.getRemark()));
        normalized.setExtraValues(normalizeExtraValues(request.getExtraValues()));
        return normalized;
    }

    private Map<String, String> normalizeExtraValues(Map<String, String> extraValues) {
        if (extraValues == null || extraValues.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        extraValues.forEach((key, value) -> {
            String normalizedKey = trimToNull(key);
            String normalizedValue = trimToNull(value);
            if (normalizedKey != null && normalizedValue != null) {
                normalized.put(normalizedKey, normalizedValue);
            }
        });
        return normalized;
    }

    private void addDraftMembers(Long teamId, List<TeamDTO.DraftMemberRequest> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        for (TeamDTO.DraftMemberRequest member : members) {
            teamMemberRepository.addDraftMember(teamId, member);
        }
        teamMemberRepository.refreshMemberCount(teamId);
    }

    private void updateTeamProfile(CurrentUser currentUser, Long teamId, TeamDTO.TeamUpdateRequest request) {
        int updated = teamRepository.updateTeamProfile(teamId, currentUser.getUserId(), normalizeUpdateRequest(request));
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
    }

    private void deleteTeamAggregate(CurrentUser currentUser, Long teamId, String action, String messagePrefix) {
        int updated = teamRepository.softDeleteTeam(teamId, currentUser.getUserId());
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        teamMemberRepository.removeMembersByTeam(teamId);
        teamInviteRepository.disableInvitesByTeam(teamId);
        teamJoinRequestRepository.closeRequestsByTeam(teamId);
        audit(currentUser, "team", action, "DELETE", messagePrefix + teamId);
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private void audit(CurrentUser currentUser, String module, String action, String type, String message) {
        if (auditPort != null) {
            auditPort.log(currentUser.getUserId(), currentUser.getUsername(), module, action, type, "SUCCESS", message);
        }
    }

    static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
