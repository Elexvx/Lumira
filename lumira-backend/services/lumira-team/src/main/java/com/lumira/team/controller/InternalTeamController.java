package com.lumira.team.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.team.app.TeamInternalApiService;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/team")
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class InternalTeamController {

    private final TeamInternalApiService teamInternalApiService;

    public InternalTeamController(TeamInternalApiService teamInternalApiService) {
        this.teamInternalApiService = teamInternalApiService;
    }

    @GetMapping("/teams/{teamId}")
    public TeamSummaryDTO getTeam(
            @RequestParam("requesterUserId") Long requesterUserId,
            @RequestParam("requesterUserUuid") String requesterUserUuid,
            @PathVariable("teamId") Long teamId
    ) {
        requireInternalServicePrincipal();
        return teamInternalApiService.getTeam(requesterUserId, requesterUserUuid, teamId);
    }

    @GetMapping("/teams/{teamId}/members")
    public List<TeamMemberDTO> listActiveMembers(
            @RequestParam("requesterUserId") Long requesterUserId,
            @RequestParam("requesterUserUuid") String requesterUserUuid,
            @PathVariable("teamId") Long teamId
    ) {
        requireInternalServicePrincipal();
        return teamInternalApiService.listActiveMembers(requesterUserId, requesterUserUuid, teamId);
    }

    @GetMapping("/teams/{teamId}/members/{userId}")
    public TeamMemberDTO requireActiveMember(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return teamInternalApiService.requireActiveMember(teamId, userId, userUuid);
    }

    @GetMapping("/users/{userId}/active-team-ids")
    public List<Long> listActiveTeamIdsForUser(
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return teamInternalApiService.listActiveTeamIdsForUser(userId, userUuid);
    }

    @GetMapping("/teams/{teamId}/members/{userId}/owner")
    public boolean isTeamOwner(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return teamInternalApiService.isTeamOwner(teamId, userId, userUuid);
    }

    @GetMapping("/teams/{teamId}/members/{userId}/admin")
    public boolean isTeamAdmin(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return teamInternalApiService.isTeamAdmin(teamId, userId, userUuid);
    }

    @GetMapping("/teams/{teamId}/members/{userId}/manager")
    public boolean isTeamManager(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    ) {
        requireInternalServicePrincipal();
        return teamInternalApiService.isTeamManager(teamId, userId, userUuid);
    }

    private void requireInternalServicePrincipal() {
        if (!AuthenticationTrustSupport.isInternalServiceAuthentication(SecurityContextHolder.getContext().getAuthentication())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Internal service token is required", "Internal service token is required");
        }
    }
}
