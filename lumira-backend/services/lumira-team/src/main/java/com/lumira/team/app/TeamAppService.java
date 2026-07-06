package com.lumira.team.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TeamAppService {
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String TEAM_TYPE_DICT_CODE = "team_type";
    private static final String TEAM_VISIBILITY_DICT_CODE = "team_visibility";
    private static final String TEAM_JOIN_MODE_DICT_CODE = "team_join_mode";
    private static final Set<String> TEAM_TYPES = Set.of("GENERAL", "DEV", "COMPETITION", "CLUB", "OTHER");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "PUBLIC");
    private static final Set<String> JOIN_MODES = Set.of("INVITE_ONLY", "APPLY", "OPEN");
    private static final Set<String> MEMBER_ROLES = Set.of("OWNER", "ADMIN", "MANAGER", "MEMBER");
    private static final String TEAM_VIEW = "team:view";
    private static final String TEAM_UPDATE = "team:update";
    private static final String TEAM_DELETE = "team:delete";
    private static final int MAX_TEAM_NAME_LENGTH = 128;
    private static final int MAX_AVATAR_URL_LENGTH = 512;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_MEMBER_NAME_LENGTH = 128;
    private static final int MAX_EMPLOYEE_NO_LENGTH = 64;
    private static final int MAX_MEMBER_REMARK_LENGTH = 512;
    private static final int MAX_INITIAL_MEMBERS = 100;
    private static final int MAX_EXTRA_VALUES = 20;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final TeamJoinRequestRepository teamJoinRequestRepository;
    private final TeamPermissionService permissionService;
    private final TeamAuditPort auditPort;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;

    public TeamAppService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamInviteRepository teamInviteRepository,
            TeamJoinRequestRepository teamJoinRequestRepository,
            TeamPermissionService permissionService,
            TeamAuditPort auditPort
    ) {
        this(
                teamRepository,
                teamMemberRepository,
                teamInviteRepository,
                teamJoinRequestRepository,
                permissionService,
                auditPort,
                null
        );
    }

    @Autowired
    public TeamAppService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamInviteRepository teamInviteRepository,
            TeamJoinRequestRepository teamJoinRequestRepository,
            TeamPermissionService permissionService,
            TeamAuditPort auditPort,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInviteRepository = teamInviteRepository;
        this.teamJoinRequestRepository = teamJoinRequestRepository;
        this.permissionService = permissionService;
        this.auditPort = auditPort;
        this.systemInternalApiProvider = systemInternalApiProvider;
    }

    @Transactional
    public TeamVO.Team createTeam(CurrentUser currentUser, TeamDTO.TeamCreateRequest request) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        TeamDTO.TeamCreateRequest normalizedRequest = normalizeCreateRequest(request);
        Long teamId = teamRepository.createTeam(teamRepository.nextTeamCode(), userId, userUuid, normalizedRequest);
        teamMemberRepository.addOwner(teamId, userId, userUuid);
        addDraftMembers(teamId, normalizedRequest.getInitialMembers());
        audit(currentUser, "team", "create", "CREATE", "Created team " + normalizedRequest.getTeamName());
        return getTeam(currentUser, teamId);
    }

    public List<TeamVO.Team> myTeams(CurrentUser currentUser) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        return teamRepository.listMyTeams(actorUserId, actorUserUuid);
    }

    public List<TeamVO.Team> listTeamsForAdmin(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, TEAM_VIEW);
        return teamRepository.listTeamsForAdmin(userId, userUuid);
    }

    public TeamVO.Team getTeam(CurrentUser currentUser, Long teamId) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        TeamVO.Team team = queryTeam(teamId, actorUserId, actorUserUuid);
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
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        String role = permissionService.activeRole(teamId, actorUserId, actorUserUuid);
        if (!permissionService.canUpdateTeam(role)) {
            throw biz(ErrorCode.FORBIDDEN, "Team update requires owner, admin, or manager");
        }
        TeamVO.Team existingTeam = requireTeamSnapshot(teamId, actorUserId, actorUserUuid);
        updateTeamProfile(actorUserId, actorUserUuid, teamId, existingTeam, request);
        audit(currentUser, "team", "update", "UPDATE", "Updated team " + teamId);
        return getTeam(currentUser, teamId);
    }

    @Transactional
    public TeamVO.Team updateTeamForAdmin(CurrentUser currentUser, Long teamId, TeamDTO.TeamUpdateRequest request) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, TEAM_UPDATE);
        requirePositiveId(teamId, "Team id is required");
        TeamVO.Team existingTeam = requireTeamSnapshot(teamId, actorUserId, actorUserUuid);
        updateTeamProfile(actorUserId, actorUserUuid, teamId, existingTeam, request);
        audit(currentUser, "team", "adminUpdate", "UPDATE", "Admin updated team " + teamId);
        TeamVO.Team team = queryTeam(teamId, actorUserId, actorUserUuid);
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        return team;
    }

    @Transactional
    public boolean deleteTeam(CurrentUser currentUser, Long teamId) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        permissionService.requireTeamOwner(teamId, actorUserId, actorUserUuid);
        TeamVO.Team team = requireTeamSnapshot(teamId, actorUserId, actorUserUuid);
        deleteTeamAggregate(currentUser, actorUserId, teamId, team, "delete", "Deleted team ");
        return true;
    }

    @Transactional
    public boolean deleteTeamForAdmin(CurrentUser currentUser, Long teamId) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePermission(currentUser, TEAM_DELETE);
        requirePositiveId(teamId, "Team id is required");
        TeamVO.Team team = requireTeamSnapshot(teamId, actorUserId, actorUserUuid);
        deleteTeamAggregate(currentUser, actorUserId, teamId, team, "adminDelete", "Admin deleted team ");
        return true;
    }

    public List<TeamVO.Member> listMembers(CurrentUser currentUser, Long teamId) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        permissionService.requireTeamMember(teamId, actorUserId, actorUserUuid);
        return teamMemberRepository.listMembers(teamId);
    }

    @Transactional
    public TeamVO.Member addMember(CurrentUser currentUser, Long teamId, TeamDTO.MemberCreateRequest request) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        String actorRole = permissionService.activeRole(teamId, actorUserId, actorUserUuid);
        if (!permissionService.canUpdateTeam(actorRole)) {
            throw biz(ErrorCode.FORBIDDEN, "Team member creation requires owner, admin, or manager");
        }
        TeamVO.Team team = requireTeamSnapshot(teamId, actorUserId, actorUserUuid);
        Long memberId = teamMemberRepository.addDraftMember(teamId, normalizeDraftMember(request));
        teamMemberRepository.refreshMemberCount(teamId, team);
        audit(currentUser, "team", "addMember", "CREATE", "Added draft team member " + memberId);
        return requireMember(teamId, memberId);
    }

    @Transactional
    public TeamVO.Member updateMemberRole(CurrentUser currentUser, Long teamId, Long memberId, TeamDTO.MemberRoleRequest request) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(memberId, "Team member id is required");
        String newRole = normalizeEnum(request == null ? null : request.getRole(), null, MEMBER_ROLES, "Invalid team member role");
        String actorRole = permissionService.activeRole(teamId, actorUserId, actorUserUuid);
        if (!TeamPermissionService.OWNER.equals(actorRole) && !TeamPermissionService.ADMIN.equals(actorRole)) {
            throw biz(ErrorCode.FORBIDDEN, "Member role updates require owner or admin");
        }
        TeamVO.Member target = requireMember(teamId, memberId);
        if (TeamPermissionService.OWNER.equals(newRole)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Use owner transfer to assign OWNER");
        }
        if (TeamPermissionService.OWNER.equals(target.getRole())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Cannot downgrade the team owner directly");
        }
        if (TeamPermissionService.ADMIN.equals(actorRole)
                && (TeamPermissionService.ADMIN.equals(target.getRole()) || TeamPermissionService.ADMIN.equals(newRole))) {
            throw biz(ErrorCode.FORBIDDEN, "Only team owner can change admin roles");
        }
        if (!teamMemberRepository.updateMemberRole(teamId, target, newRole)) {
            throw biz(ErrorCode.NOT_FOUND, "Team member not found");
        }
        return requireMember(teamId, memberId);
    }

    @Transactional
    public boolean removeMember(CurrentUser currentUser, Long teamId, Long memberId) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(memberId, "Team member id is required");
        TeamVO.Member actor = permissionService.activeMember(teamId, actorUserId, actorUserUuid);
        TeamVO.Member target = requireMember(teamId, memberId);
        boolean self = Objects.equals(target.getUserId(), actorUserId);
        if (!permissionService.canRemoveMember(actor == null ? null : actor.getRole(), target.getRole(), self)) {
            throw biz(ErrorCode.FORBIDDEN, "Cannot remove this team member");
        }
        if (!teamMemberRepository.removeMember(teamId, target)) {
            throw biz(ErrorCode.NOT_FOUND, "Team member not found");
        }
        TeamVO.Team team = requireTeamSnapshot(teamId, actorUserId, actorUserUuid);
        teamMemberRepository.refreshMemberCount(teamId, team);
        return true;
    }

    @Transactional
    public boolean leaveTeam(CurrentUser currentUser, Long teamId) {
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        TeamVO.Member member = permissionService.activeMember(teamId, actorUserId, actorUserUuid);
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
        Long actorUserId = requireUserId(currentUser);
        String actorUserUuid = requireUserUuid(currentUser);
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(request == null ? null : request.getMemberId(), "Team member id is required");
        permissionService.requireTeamOwner(teamId, actorUserId, actorUserUuid);
        TeamVO.Member target = requireMember(teamId, request.getMemberId());
        if (!"ACTIVE".equals(target.getStatus())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "New owner must be an active member");
        }
        requirePositiveId(target.getUserId(), "New owner user id is required");
        requireText(target.getUserUuid(), "New owner user uuid is required");
        String previousRole = normalizeEnum(request.getPreviousOwnerRole(), "ADMIN", Set.of("ADMIN", "MANAGER", "MEMBER"), "Invalid previous owner role");
        boolean memberRolesTransferred = teamMemberRepository.transferOwner(teamId, actorUserId, actorUserUuid, previousRole, target.getId(), target.getUserId(), target.getUserUuid());
        if (!memberRolesTransferred) {
            throw biz(ErrorCode.BIZ_ERROR, "Team ownership changed, please retry");
        }
        int transferred = teamRepository.transferOwner(teamId, actorUserId, actorUserUuid, target.getUserId(), target.getUserUuid(), actorUserId, actorUserUuid);
        if (transferred == 0) {
            throw biz(ErrorCode.BIZ_ERROR, "Team ownership changed, please retry");
        }
        audit(currentUser, "team", "transferOwner", "UPDATE", "Transferred team owner " + teamId);
        return getTeam(currentUser, teamId);
    }

    TeamVO.Team queryTeam(Long teamId, Long currentUserId, String currentUserUuid) {
        requirePositiveId(teamId, "Team id is required");
        if (currentUserId != null) {
            requirePositiveId(currentUserId, "User id is required");
        }
        return teamRepository.findTeam(teamId, currentUserId, currentUserUuid);
    }

    TeamVO.Member requireMember(Long teamId, Long memberId) {
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(memberId, "Team member id is required");
        TeamVO.Member member = teamMemberRepository.findMemberById(teamId, memberId);
        if (member == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team member not found");
        }
        return member;
    }

    void ensureDirectMember(Long teamId, Long userId, String userUuid, Long invitedBy, String invitedByUuid, String role) {
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(userId, "User id is required");
        if (invitedBy != null) {
            requirePositiveId(invitedBy, "Inviter user id is required");
            requireText(invitedByUuid, "Inviter user uuid is required");
        }
        String normalizedRole = normalizeEnum(role, "MEMBER", MEMBER_ROLES, "Invalid team member role");
        requireText(userUuid, "User uuid is required");
        TeamVO.Team team = requireTeamBoundarySnapshot(teamId);
        teamMemberRepository.ensureDirectMember(teamId, userId, userUuid.trim(), invitedBy, invitedByUuid == null ? null : invitedByUuid.trim(), normalizedRole);
        teamMemberRepository.refreshMemberCount(teamId, team);
    }

    void refreshMemberCount(Long teamId) {
        requirePositiveId(teamId, "Team id is required");
        teamMemberRepository.refreshMemberCount(teamId, requireTeamBoundarySnapshot(teamId));
    }

    Long requireUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    String requireUserUuid(CurrentUser currentUser) {
        requireUserId(currentUser);
        return requireText(currentUser.getUserUuid(), "User uuid is required");
    }

    private void requirePermission(CurrentUser currentUser, String permissionKey) {
        requireUserId(currentUser);
        Set<String> permissions = currentUser.getPermissions();
        if (permissions == null || permissions.isEmpty() || (!permissions.contains("*") && !permissions.contains(permissionKey))) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
    }

    private String trustedUsername(CurrentUser currentUser) {
        requireUserId(currentUser);
        return currentUser.getUsername();
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) || systemInternalApiProvider == null) {
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(userId, userSnapshot.userUuid().trim());
        if (snapshot == null || !StringUtils.hasText(snapshot.version())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username());
        currentUser.setPermissions(trustedPermissions(snapshot));
        currentUser.setRoleIds(trustedLongSet(snapshot.roleIds()));
        currentUser.setPrimaryDeptId(snapshot.primaryDeptId());
        currentUser.setDeptIds(trustedLongSet(snapshot.deptIds()));
        currentUser.setDescendantDeptIds(trustedLongSet(snapshot.descendantDeptIds()));
        currentUser.setDataScopes(snapshot.dataScopes() == null ? List.of() : List.copyOf(snapshot.dataScopes()));
        currentUser.setPermissionsVersion(snapshot.version());
        currentUser.setDefaultHomePath(snapshot.defaultHomePath());
    }

    private Set<String> trustedPermissions(PermissionSnapshotDTO snapshot) {
        return snapshot == null || snapshot.permissions() == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(snapshot.permissions()));
    }

    private Set<Long> trustedLongSet(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null && value > 0) {
                normalized.add(value);
            }
        }
        return Set.copyOf(normalized);
    }

    void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
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

    String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw biz(ErrorCode.UNAUTHORIZED, message);
        }
        return normalized;
    }

    private TeamDTO.TeamCreateRequest normalizeCreateRequest(TeamDTO.TeamCreateRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Team request is required");
        }
        TeamDTO.TeamCreateRequest normalized = new TeamDTO.TeamCreateRequest();
        normalized.setTeamName(trimRequired(request.getTeamName(), "Team name is required", MAX_TEAM_NAME_LENGTH));
        normalized.setTeamType(normalizeDictValue(request.getTeamType(), "GENERAL", TEAM_TYPE_DICT_CODE, TEAM_TYPES, "Invalid team type"));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl(), MAX_AVATAR_URL_LENGTH, "Avatar url is too long"));
        normalized.setDescription(trimToNull(request.getDescription(), MAX_DESCRIPTION_LENGTH, "Team description is too long"));
        normalized.setVisibility(normalizeDictValue(request.getVisibility(), "PRIVATE", TEAM_VISIBILITY_DICT_CODE, VISIBILITIES, "Invalid team visibility"));
        normalized.setJoinMode(normalizeDictValue(request.getJoinMode(), "INVITE_ONLY", TEAM_JOIN_MODE_DICT_CODE, JOIN_MODES, "Invalid join mode"));
        if (request.getInitialMembers() != null) {
            if (request.getInitialMembers().size() > MAX_INITIAL_MEMBERS) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Initial members exceed limit");
            }
            normalized.setInitialMembers(request.getInitialMembers().stream()
                    .map(this::normalizeDraftMember)
                    .toList());
        }
        return normalized;
    }

    private TeamDTO.TeamUpdateRequest normalizeUpdateRequest(TeamDTO.TeamUpdateRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Team request is required");
        }
        TeamDTO.TeamUpdateRequest normalized = new TeamDTO.TeamUpdateRequest();
        normalized.setTeamName(trimRequired(request.getTeamName(), "Team name is required", MAX_TEAM_NAME_LENGTH));
        normalized.setTeamType(normalizeDictValue(request.getTeamType(), "GENERAL", TEAM_TYPE_DICT_CODE, TEAM_TYPES, "Invalid team type"));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl(), MAX_AVATAR_URL_LENGTH, "Avatar url is too long"));
        normalized.setDescription(trimToNull(request.getDescription(), MAX_DESCRIPTION_LENGTH, "Team description is too long"));
        normalized.setVisibility(normalizeDictValue(request.getVisibility(), "PRIVATE", TEAM_VISIBILITY_DICT_CODE, VISIBILITIES, "Invalid team visibility"));
        normalized.setJoinMode(normalizeDictValue(request.getJoinMode(), "INVITE_ONLY", TEAM_JOIN_MODE_DICT_CODE, JOIN_MODES, "Invalid join mode"));
        return normalized;
    }

    private TeamDTO.DraftMemberRequest normalizeDraftMember(TeamDTO.DraftMemberRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Team member request is required");
        }
        TeamDTO.DraftMemberRequest normalized = new TeamDTO.DraftMemberRequest();
        normalized.setMemberName(trimRequired(request.getMemberName(), "Member name is required", MAX_MEMBER_NAME_LENGTH));
        normalized.setEmployeeNo(trimToNull(request.getEmployeeNo(), MAX_EMPLOYEE_NO_LENGTH, "Employee no is too long"));
        normalized.setDepartmentName(trimToNull(request.getDepartmentName(), MAX_MEMBER_NAME_LENGTH, "Department name is too long"));
        normalized.setRole(normalizeEnum(request.getRole(), "MEMBER", MEMBER_ROLES, "Invalid team member role"));
        if ("OWNER".equals(normalized.getRole())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Draft members cannot be owner");
        }
        normalized.setRemark(trimToNull(request.getRemark(), MAX_MEMBER_REMARK_LENGTH, "Member remark is too long"));
        normalized.setExtraValues(normalizeExtraValues(request.getExtraValues()));
        return normalized;
    }

    private Map<String, String> normalizeExtraValues(Map<String, String> extraValues) {
        if (extraValues == null || extraValues.isEmpty()) {
            return Map.of();
        }
        if (extraValues.size() > MAX_EXTRA_VALUES) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Extra values exceed limit");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        extraValues.forEach((key, value) -> {
            String normalizedKey = trimToNull(key, 64, "Extra value key is too long");
            String normalizedValue = trimToNull(value, 512, "Extra value is too long");
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
        teamMemberRepository.refreshMemberCount(teamId, requireTeamBoundarySnapshot(teamId));
    }

    private void updateTeamProfile(Long actorUserId, String actorUserUuid, Long teamId, TeamVO.Team team, TeamDTO.TeamUpdateRequest request) {
        int updated = teamRepository.updateTeamProfile(teamId, team, actorUserId, actorUserUuid, normalizeUpdateRequest(request));
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
    }

    private void deleteTeamAggregate(CurrentUser currentUser, Long actorUserId, Long teamId, TeamVO.Team team, String action, String messagePrefix) {
        String actorUserUuid = requireUserUuid(currentUser);
        int updated = teamRepository.softDeleteTeam(teamId, team, actorUserId, actorUserUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        teamMemberRepository.removeMembersByTeam(teamId, team);
        teamInviteRepository.disableInvitesByTeam(teamId, team, actorUserId, actorUserUuid);
        teamJoinRequestRepository.closeRequestsByTeam(teamId, team);
        audit(currentUser, "team", action, "DELETE", messagePrefix + teamId);
    }

    private TeamVO.Team requireTeamSnapshot(Long teamId, Long actorUserId, String actorUserUuid) {
        TeamVO.Team team = queryTeam(teamId, actorUserId, actorUserUuid);
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        return team;
    }

    private TeamVO.Team requireTeamBoundarySnapshot(Long teamId) {
        TeamVO.Team team = queryTeam(teamId, null, null);
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        return team;
    }

    private String trimRequired(String value, String message) {
        return trimRequired(value, message, Integer.MAX_VALUE);
    }

    private String trimRequired(String value, String message, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        if (trimmed.length() > maxLength) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private String trimToNull(String value, int maxLength, String message) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > maxLength) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private void audit(CurrentUser currentUser, String module, String action, String type, String message) {
        if (auditPort != null) {
            auditPort.log(requireUserId(currentUser), requireUserUuid(currentUser), trustedUsername(currentUser), module, action, type, "SUCCESS", message);
        }
    }

    static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
