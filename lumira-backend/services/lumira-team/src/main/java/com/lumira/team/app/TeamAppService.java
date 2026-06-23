package com.lumira.team.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PlatformContext;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
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
        Long tenantId = requireTenantId(currentUser);
        Long userId = requireUserId(currentUser);
        TeamDTO.TeamCreateRequest normalizedRequest = normalizeCreateRequest(tenantId, request);
        Long teamId = teamRepository.createTeam(tenantId, teamRepository.nextTeamCode(tenantId), userId, normalizedRequest);
        teamMemberRepository.addOwner(tenantId, teamId, userId);
        addDraftMembers(tenantId, teamId, normalizedRequest.getInitialMembers());
        audit(currentUser, "team", "create", "CREATE", "Created team " + normalizedRequest.getTeamName());
        return getTeam(currentUser, teamId);
    }

    public List<TeamVO.Team> myTeams(CurrentUser currentUser) {
        Long tenantId = requireTenantId(currentUser);
        return teamRepository.listMyTeams(tenantId, currentUser.getUserId());
    }

    public List<TeamVO.Team> listTeamsForAdmin(CurrentUser currentUser) {
        Long tenantId = requireTenantId(currentUser);
        Long userId = requireUserId(currentUser);
        return teamRepository.listTeamsForAdmin(tenantId, userId);
    }

    public TeamVO.Team getTeam(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        TeamVO.Team team = queryTeam(tenantId, teamId, currentUser.getUserId());
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
        Long tenantId = requireTenantId(currentUser);
        String role = permissionService.activeRole(tenantId, teamId, currentUser.getUserId());
        if (!permissionService.canUpdateTeam(role)) {
            throw biz(ErrorCode.FORBIDDEN, "Team update requires owner, admin, or manager");
        }
        updateTeamProfile(currentUser, tenantId, teamId, request);
        audit(currentUser, "team", "update", "UPDATE", "Updated team " + teamId);
        return getTeam(currentUser, teamId);
    }

    @Transactional
    public TeamVO.Team updateTeamForAdmin(CurrentUser currentUser, Long teamId, TeamDTO.TeamUpdateRequest request) {
        Long tenantId = requireTenantId(currentUser);
        updateTeamProfile(currentUser, tenantId, teamId, request);
        audit(currentUser, "team", "adminUpdate", "UPDATE", "Admin updated team " + teamId);
        TeamVO.Team team = queryTeam(tenantId, teamId, currentUser.getUserId());
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        return team;
    }

    @Transactional
    public boolean deleteTeam(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        permissionService.requireTeamOwner(tenantId, teamId, currentUser.getUserId());
        deleteTeamAggregate(currentUser, tenantId, teamId, "delete", "Deleted team ");
        return true;
    }

    @Transactional
    public boolean deleteTeamForAdmin(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        deleteTeamAggregate(currentUser, tenantId, teamId, "adminDelete", "Admin deleted team ");
        return true;
    }

    public List<TeamVO.Member> listMembers(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        permissionService.requireTeamMember(tenantId, teamId, currentUser.getUserId());
        return teamMemberRepository.listMembers(tenantId, teamId);
    }

    @Transactional
    public TeamVO.Member addMember(CurrentUser currentUser, Long teamId, TeamDTO.MemberCreateRequest request) {
        Long tenantId = requireTenantId(currentUser);
        String actorRole = permissionService.activeRole(tenantId, teamId, currentUser.getUserId());
        if (!permissionService.canUpdateTeam(actorRole)) {
            throw biz(ErrorCode.FORBIDDEN, "Team member creation requires owner, admin, or manager");
        }
        Long memberId = teamMemberRepository.addDraftMember(tenantId, teamId, normalizeDraftMember(request));
        teamMemberRepository.refreshMemberCount(tenantId, teamId);
        audit(currentUser, "team", "addMember", "CREATE", "Added draft team member " + memberId);
        return requireMember(tenantId, teamId, memberId);
    }

    @Transactional
    public TeamVO.Member updateMemberRole(CurrentUser currentUser, Long teamId, Long memberId, TeamDTO.MemberRoleRequest request) {
        Long tenantId = requireTenantId(currentUser);
        String actorRole = permissionService.activeRole(tenantId, teamId, currentUser.getUserId());
        if (!TeamPermissionService.OWNER.equals(actorRole) && !TeamPermissionService.ADMIN.equals(actorRole)) {
            throw biz(ErrorCode.FORBIDDEN, "Member role updates require owner or admin");
        }
        TeamVO.Member target = requireMember(tenantId, teamId, memberId);
        String newRole = normalizeEnum(request.getRole(), null, MEMBER_ROLES, "Invalid team member role");
        if (TeamPermissionService.OWNER.equals(newRole)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Use owner transfer to assign OWNER");
        }
        if (TeamPermissionService.OWNER.equals(target.getRole())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Cannot downgrade the team owner directly");
        }
        teamMemberRepository.updateMemberRole(tenantId, teamId, memberId, newRole);
        return requireMember(tenantId, teamId, memberId);
    }

    @Transactional
    public boolean removeMember(CurrentUser currentUser, Long teamId, Long memberId) {
        Long tenantId = requireTenantId(currentUser);
        TeamVO.Member actor = permissionService.activeMember(tenantId, teamId, currentUser.getUserId());
        TeamVO.Member target = requireMember(tenantId, teamId, memberId);
        boolean self = target.getUserId().equals(currentUser.getUserId());
        if (!permissionService.canRemoveMember(actor == null ? null : actor.getRole(), target.getRole(), self)) {
            throw biz(ErrorCode.FORBIDDEN, "Cannot remove this team member");
        }
        teamMemberRepository.removeMember(tenantId, teamId, memberId);
        teamMemberRepository.refreshMemberCount(tenantId, teamId);
        return true;
    }

    @Transactional
    public boolean leaveTeam(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        TeamVO.Member member = permissionService.activeMember(tenantId, teamId, currentUser.getUserId());
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
        Long tenantId = requireTenantId(currentUser);
        permissionService.requireTeamOwner(tenantId, teamId, currentUser.getUserId());
        TeamVO.Member target = requireMember(tenantId, teamId, request.getMemberId());
        if (!"ACTIVE".equals(target.getStatus())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "New owner must be an active member");
        }
        String previousRole = normalizeEnum(request.getPreviousOwnerRole(), "ADMIN", Set.of("ADMIN", "MANAGER", "MEMBER"), "Invalid previous owner role");
        teamMemberRepository.transferOwner(tenantId, teamId, currentUser.getUserId(), previousRole, target.getId());
        teamRepository.transferOwner(tenantId, teamId, target.getUserId(), currentUser.getUserId());
        audit(currentUser, "team", "transferOwner", "UPDATE", "Transferred team owner " + teamId);
        return getTeam(currentUser, teamId);
    }

    TeamVO.Team queryTeam(Long tenantId, Long teamId, Long currentUserId) {
        return teamRepository.findTeam(tenantId, teamId, currentUserId);
    }

    TeamVO.Member requireMember(Long tenantId, Long teamId, Long memberId) {
        TeamVO.Member member = teamMemberRepository.findMemberById(tenantId, teamId, memberId);
        if (member == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team member not found");
        }
        return member;
    }

    void ensureDirectMember(Long tenantId, Long teamId, Long userId, Long invitedBy, String role) {
        teamMemberRepository.ensureDirectMember(tenantId, teamId, userId, invitedBy, role);
        teamMemberRepository.refreshMemberCount(tenantId, teamId);
    }

    void refreshMemberCount(Long tenantId, Long teamId) {
        teamMemberRepository.refreshMemberCount(tenantId, teamId);
    }

    Long requireTenantId(CurrentUser currentUser) {
        if (currentUser == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return PlatformContext.compatibilityTenantId();
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

    private String normalizeDictValue(Long tenantId, String value, String defaultValue, String dictCode, Set<String> fallbackAllowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        Set<String> allowed = teamRepository.loadEnabledDictValues(tenantId, dictCode);
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

    private TeamDTO.TeamCreateRequest normalizeCreateRequest(Long tenantId, TeamDTO.TeamCreateRequest request) {
        TeamDTO.TeamCreateRequest normalized = new TeamDTO.TeamCreateRequest();
        normalized.setTeamName(trimRequired(request.getTeamName(), "Team name is required"));
        normalized.setTeamType(normalizeDictValue(tenantId, request.getTeamType(), "GENERAL", TEAM_TYPE_DICT_CODE, TEAM_TYPES, "Invalid team type"));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setVisibility(normalizeDictValue(tenantId, request.getVisibility(), "PRIVATE", TEAM_VISIBILITY_DICT_CODE, VISIBILITIES, "Invalid team visibility"));
        normalized.setJoinMode(normalizeDictValue(tenantId, request.getJoinMode(), "INVITE_ONLY", TEAM_JOIN_MODE_DICT_CODE, JOIN_MODES, "Invalid join mode"));
        if (request.getInitialMembers() != null) {
            normalized.setInitialMembers(request.getInitialMembers().stream()
                    .map(this::normalizeDraftMember)
                    .toList());
        }
        return normalized;
    }

    private TeamDTO.TeamUpdateRequest normalizeUpdateRequest(Long tenantId, TeamDTO.TeamUpdateRequest request) {
        TeamDTO.TeamUpdateRequest normalized = new TeamDTO.TeamUpdateRequest();
        normalized.setTeamName(trimRequired(request.getTeamName(), "Team name is required"));
        normalized.setTeamType(normalizeDictValue(tenantId, request.getTeamType(), "GENERAL", TEAM_TYPE_DICT_CODE, TEAM_TYPES, "Invalid team type"));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setVisibility(normalizeDictValue(tenantId, request.getVisibility(), "PRIVATE", TEAM_VISIBILITY_DICT_CODE, VISIBILITIES, "Invalid team visibility"));
        normalized.setJoinMode(normalizeDictValue(tenantId, request.getJoinMode(), "INVITE_ONLY", TEAM_JOIN_MODE_DICT_CODE, JOIN_MODES, "Invalid join mode"));
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
        return normalized;
    }

    private void addDraftMembers(Long tenantId, Long teamId, List<TeamDTO.DraftMemberRequest> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        for (TeamDTO.DraftMemberRequest member : members) {
            teamMemberRepository.addDraftMember(tenantId, teamId, member);
        }
        teamMemberRepository.refreshMemberCount(tenantId, teamId);
    }

    private void updateTeamProfile(CurrentUser currentUser, Long tenantId, Long teamId, TeamDTO.TeamUpdateRequest request) {
        int updated = teamRepository.updateTeamProfile(tenantId, teamId, currentUser.getUserId(), normalizeUpdateRequest(tenantId, request));
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
    }

    private void deleteTeamAggregate(CurrentUser currentUser, Long tenantId, Long teamId, String action, String messagePrefix) {
        int updated = teamRepository.softDeleteTeam(tenantId, teamId, currentUser.getUserId());
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        teamMemberRepository.removeMembersByTeam(tenantId, teamId);
        teamInviteRepository.disableInvitesByTeam(tenantId, teamId);
        teamJoinRequestRepository.closeRequestsByTeam(tenantId, teamId);
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
            auditPort.log(PlatformContext.compatibilityTenantId(), currentUser.getUserId(), currentUser.getUsername(), module, action, type, "SUCCESS", message);
        }
    }

    static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
