package com.lumira.saas.modules.team.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.modules.team.app.TeamAppService;
import com.lumira.saas.modules.team.app.TeamInviteService;
import com.lumira.saas.modules.team.dto.TeamDTO;
import com.lumira.saas.modules.team.vo.TeamVO;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v2")
public class TeamV2Controller {
    private final TeamAppService teamAppService;
    private final TeamInviteService teamInviteService;
    private final SecurityContextFacade securityContextFacade;

    public TeamV2Controller(TeamAppService teamAppService, TeamInviteService teamInviteService, SecurityContextFacade securityContextFacade) {
        this.teamAppService = teamAppService;
        this.teamInviteService = teamInviteService;
        this.securityContextFacade = securityContextFacade;
    }

    @PostMapping("/teams")
    @RepeatSubmit
    public ApiResponse<TeamVO.Team> createTeam(@Valid @RequestBody TeamDTO.TeamCreateRequest request) {
        return ApiResponse.success(teamAppService.createTeam(currentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/teams/my")
    public ApiResponse<List<TeamVO.Team>> myTeams() {
        return ApiResponse.success(teamAppService.myTeams(currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/teams/{teamId}")
    public ApiResponse<TeamVO.Team> team(@PathVariable("teamId") Long teamId) {
        return ApiResponse.success(teamAppService.getTeam(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PutMapping("/teams/{teamId}")
    @RepeatSubmit
    public ApiResponse<TeamVO.Team> updateTeam(@PathVariable("teamId") Long teamId, @Valid @RequestBody TeamDTO.TeamUpdateRequest request) {
        return ApiResponse.success(teamAppService.updateTeam(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/teams/{teamId}")
    public ApiResponse<Boolean> deleteTeam(@PathVariable("teamId") Long teamId) {
        return ApiResponse.success(teamAppService.deleteTeam(currentUser(), teamId), TraceContext.getRequestId());
    }

    @GetMapping("/teams/{teamId}/members")
    public ApiResponse<List<TeamVO.Member>> members(@PathVariable("teamId") Long teamId) {
        return ApiResponse.success(teamAppService.listMembers(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PatchMapping("/teams/{teamId}/members/{memberId}/role")
    @RepeatSubmit
    public ApiResponse<TeamVO.Member> updateMemberRole(
            @PathVariable("teamId") Long teamId,
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody TeamDTO.MemberRoleRequest request
    ) {
        return ApiResponse.success(teamAppService.updateMemberRole(currentUser(), teamId, memberId, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/teams/{teamId}/members/{memberId}")
    public ApiResponse<Boolean> removeMember(@PathVariable("teamId") Long teamId, @PathVariable("memberId") Long memberId) {
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
        return ApiResponse.success(teamAppService.transferOwner(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/invites")
    @RepeatSubmit
    public ApiResponse<TeamVO.Invite> createInvite(@PathVariable("teamId") Long teamId, @Valid @RequestBody TeamDTO.InviteCreateRequest request) {
        return ApiResponse.success(teamInviteService.createInvite(currentUser(), teamId, request), TraceContext.getRequestId());
    }

    @GetMapping("/teams/{teamId}/invites")
    public ApiResponse<List<TeamVO.Invite>> invites(@PathVariable("teamId") Long teamId) {
        return ApiResponse.success(teamInviteService.listInvites(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PatchMapping("/teams/{teamId}/invites/{inviteId}/disable")
    public ApiResponse<Boolean> disableInvite(@PathVariable("teamId") Long teamId, @PathVariable("inviteId") Long inviteId) {
        return ApiResponse.success(teamInviteService.disableInvite(currentUser(), teamId, inviteId), TraceContext.getRequestId());
    }

    @PostMapping("/team-invites/preview")
    public ApiResponse<TeamVO.Invite> previewInvite(@RequestBody TeamDTO.InviteTokenRequest request) {
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
        return ApiResponse.success(teamInviteService.listJoinRequests(currentUser(), teamId), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/join-requests/{requestId}/approve")
    @RepeatSubmit
    public ApiResponse<TeamVO.JoinRequest> approveJoinRequest(
            @PathVariable("teamId") Long teamId,
            @PathVariable("requestId") Long requestId,
            @RequestBody(required = false) TeamDTO.JoinReviewRequest request
    ) {
        return ApiResponse.success(teamInviteService.approveJoinRequest(currentUser(), teamId, requestId, request), TraceContext.getRequestId());
    }

    @PostMapping("/teams/{teamId}/join-requests/{requestId}/reject")
    @RepeatSubmit
    public ApiResponse<TeamVO.JoinRequest> rejectJoinRequest(
            @PathVariable("teamId") Long teamId,
            @PathVariable("requestId") Long requestId,
            @RequestBody(required = false) TeamDTO.JoinReviewRequest request
    ) {
        return ApiResponse.success(teamInviteService.rejectJoinRequest(currentUser(), teamId, requestId, request), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }
}
