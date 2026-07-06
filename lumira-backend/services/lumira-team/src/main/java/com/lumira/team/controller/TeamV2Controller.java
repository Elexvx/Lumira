package com.lumira.team.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.team.security.RepeatSubmit;
import com.lumira.team.app.TeamAppService;
import com.lumira.team.app.TeamInviteService;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v2")
public class TeamV2Controller {
    private static final String TEAM_VIEW = "team:view";
    private static final String TEAM_CREATE = "team:create";
    private static final String TEAM_UPDATE = "team:update";
    private static final String TEAM_DELETE = "team:delete";
    private static final String TEAM_MEMBER_VIEW = "team:member:view";
    private static final String TEAM_MEMBER_INVITE = "team:member:invite";
    private static final String TEAM_MEMBER_REMOVE = "team:member:remove";
    private static final String TEAM_MEMBER_ROLE_UPDATE = "team:member:role-update";
    private static final String STATUS_ENABLED = "ENABLED";

    private final TeamAppService teamAppService;
    private final TeamInviteService teamInviteService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SystemInternalApi systemInternalApi;

    public TeamV2Controller(
            TeamAppService teamAppService,
            TeamInviteService teamInviteService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(teamAppService, teamInviteService, securityContextFacade, permissionGuard, null);
    }

    @Autowired
    public TeamV2Controller(
            TeamAppService teamAppService,
            TeamInviteService teamInviteService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi
    ) {
        this.teamAppService = teamAppService;
        this.teamInviteService = teamInviteService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.systemInternalApi = systemInternalApi;
    }

    @PostMapping("/teams")
    @RepeatSubmit
    public ApiResponse<TeamVO.Team> createTeam(@Valid @RequestBody TeamDTO.TeamCreateRequest request) {
        require(TEAM_CREATE);
        return ApiResponse.success(teamAppService.createTeam(currentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/teams/my")
    public ApiResponse<List<TeamVO.Team>> myTeams() {
        return ApiResponse.success(teamAppService.myTeams(currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/admin/teams")
    public ApiResponse<List<TeamVO.Team>> adminTeams() {
        require(TEAM_VIEW);
        return ApiResponse.success(teamAppService.listTeamsForAdmin(currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/teams/{teamId}")
    public ApiResponse<TeamVO.Team> team(@PathVariable("teamId") Long teamId) {
        return ApiResponse.success(teamAppService.getTeam(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PutMapping("/teams/{teamId}")
    @RepeatSubmit
    public ApiResponse<TeamVO.Team> updateTeam(@PathVariable("teamId") Long teamId, @Valid @RequestBody TeamDTO.TeamUpdateRequest request) {
        require(TEAM_UPDATE);
        return ApiResponse.success(teamAppService.updateTeam(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @PutMapping("/admin/teams/{teamId}")
    @RepeatSubmit
    public ApiResponse<TeamVO.Team> adminUpdateTeam(@PathVariable("teamId") Long teamId, @Valid @RequestBody TeamDTO.TeamUpdateRequest request) {
        require(TEAM_UPDATE);
        return ApiResponse.success(teamAppService.updateTeamForAdmin(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/teams/{teamId}")
    public ApiResponse<Boolean> deleteTeam(@PathVariable("teamId") Long teamId) {
        require(TEAM_DELETE);
        return ApiResponse.success(teamAppService.deleteTeam(currentUser(), teamId), TraceContext.getRequestId());
    }

    @DeleteMapping("/admin/teams/{teamId}")
    public ApiResponse<Boolean> adminDeleteTeam(@PathVariable("teamId") Long teamId) {
        require(TEAM_DELETE);
        return ApiResponse.success(teamAppService.deleteTeamForAdmin(currentUser(), teamId), TraceContext.getRequestId());
    }

    @GetMapping("/teams/{teamId}/members")
    public ApiResponse<List<TeamVO.Member>> members(@PathVariable("teamId") Long teamId) {
        require(TEAM_MEMBER_VIEW);
        return ApiResponse.success(teamAppService.listMembers(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/members")
    @RepeatSubmit
    public ApiResponse<TeamVO.Member> addMember(
            @PathVariable("teamId") Long teamId,
            @Valid @RequestBody TeamDTO.MemberCreateRequest request
    ) {
        require(TEAM_MEMBER_INVITE);
        return ApiResponse.success(teamAppService.addMember(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @PatchMapping("/teams/{teamId}/members/{memberId}/role")
    @RepeatSubmit
    public ApiResponse<TeamVO.Member> updateMemberRole(
            @PathVariable("teamId") Long teamId,
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody TeamDTO.MemberRoleRequest request
    ) {
        require(TEAM_MEMBER_ROLE_UPDATE);
        return ApiResponse.success(teamAppService.updateMemberRole(currentUser(), teamId, memberId, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/teams/{teamId}/members/{memberId}")
    public ApiResponse<Boolean> removeMember(@PathVariable("teamId") Long teamId, @PathVariable("memberId") Long memberId) {
        require(TEAM_MEMBER_REMOVE);
        return ApiResponse.success(teamAppService.removeMember(currentUser(), teamId, memberId), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/leave")
    @RepeatSubmit
    public ApiResponse<Boolean> leaveTeam(@PathVariable("teamId") Long teamId) {
        return ApiResponse.success(teamAppService.leaveTeam(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/transfer-owner")
    @RepeatSubmit
    public ApiResponse<TeamVO.Team> transferOwner(@PathVariable("teamId") Long teamId, @Valid @RequestBody TeamDTO.TransferOwnerRequest request) {
        require(TEAM_MEMBER_ROLE_UPDATE);
        return ApiResponse.success(teamAppService.transferOwner(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/invites")
    @RepeatSubmit
    public ApiResponse<TeamVO.Invite> createInvite(@PathVariable("teamId") Long teamId, @Valid @RequestBody TeamDTO.InviteCreateRequest request) {
        require(TEAM_MEMBER_INVITE);
        return ApiResponse.success(teamInviteService.createInvite(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @GetMapping("/teams/{teamId}/invites")
    public ApiResponse<List<TeamVO.Invite>> invites(@PathVariable("teamId") Long teamId) {
        require(TEAM_MEMBER_INVITE);
        return ApiResponse.success(teamInviteService.listInvites(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PatchMapping("/teams/{teamId}/invites/{inviteId}/disable")
    public ApiResponse<Boolean> disableInvite(@PathVariable("teamId") Long teamId, @PathVariable("inviteId") Long inviteId) {
        require(TEAM_MEMBER_INVITE);
        return ApiResponse.success(teamInviteService.disableInvite(currentUser(), teamId, inviteId), TraceContext.getRequestId());
    }

    @PostMapping("/team-invites/preview")
    public ApiResponse<TeamVO.InvitePreview> previewInvite(@RequestBody TeamDTO.InviteTokenRequest request) {
        return ApiResponse.success(teamInviteService.previewByToken(request.getToken()), TraceContext.getRequestId());
    }

    @PostMapping("/team-invites/join")
    @RepeatSubmit
    public ApiResponse<TeamVO.JoinResult> joinByToken(@RequestBody TeamDTO.InviteTokenRequest request) {
        return ApiResponse.success(teamInviteService.joinByToken(currentUser(), request.getToken()), TraceContext.getRequestId());
    }

    @PostMapping("/team-invites/join-by-code")
    @RepeatSubmit
    public ApiResponse<TeamVO.JoinResult> joinByCode(@RequestBody TeamDTO.InviteCodeJoinRequest request) {
        return ApiResponse.success(teamInviteService.joinByCode(currentUser(), request.getInviteCode()), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/join-requests")
    @RepeatSubmit
    public ApiResponse<TeamVO.JoinResult> createJoinRequest(@PathVariable("teamId") Long teamId, @RequestBody(required = false) TeamDTO.JoinRequestCreateRequest request) {
        return ApiResponse.success(teamInviteService.createJoinRequest(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @GetMapping("/teams/{teamId}/join-requests")
    public ApiResponse<List<TeamVO.JoinRequest>> joinRequests(@PathVariable("teamId") Long teamId) {
        require(TEAM_MEMBER_INVITE);
        return ApiResponse.success(teamInviteService.listJoinRequests(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/join-requests/{requestId}/approve")
    @RepeatSubmit
    public ApiResponse<TeamVO.JoinRequest> approveJoinRequest(
            @PathVariable("teamId") Long teamId,
            @PathVariable("requestId") Long requestId,
            @RequestBody(required = false) TeamDTO.JoinReviewRequest request
    ) {
        require(TEAM_MEMBER_INVITE);
        return ApiResponse.success(teamInviteService.approveJoinRequest(currentUser(), teamId, requestId, request), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/join-requests/{requestId}/reject")
    @RepeatSubmit
    public ApiResponse<TeamVO.JoinRequest> rejectJoinRequest(
            @PathVariable("teamId") Long teamId,
            @PathVariable("requestId") Long requestId,
            @RequestBody(required = false) TeamDTO.JoinReviewRequest request
    ) {
        require(TEAM_MEMBER_INVITE);
        return ApiResponse.success(teamInviteService.rejectJoinRequest(currentUser(), teamId, requestId, request), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshTrustedCurrentUser(currentUser);
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) || systemInternalApi == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(
                userId,
                userSnapshot.userUuid().trim()
        );
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username());
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }
}
