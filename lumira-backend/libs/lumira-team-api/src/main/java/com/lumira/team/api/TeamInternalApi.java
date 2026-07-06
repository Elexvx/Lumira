package com.lumira.team.api;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface TeamInternalApi {
    @GetExchange("/internal/team/teams/{teamId}")
    TeamSummaryDTO getTeam(
            @RequestParam("requesterUserId") Long requesterUserId,
            @RequestParam("requesterUserUuid") String requesterUserUuid,
            @PathVariable("teamId") Long teamId
    );

    @GetExchange("/internal/team/teams/{teamId}/members")
    List<TeamMemberDTO> listActiveMembers(
            @RequestParam("requesterUserId") Long requesterUserId,
            @RequestParam("requesterUserUuid") String requesterUserUuid,
            @PathVariable("teamId") Long teamId
    );

    @GetExchange("/internal/team/teams/{teamId}/members/{userId}")
    TeamMemberDTO requireActiveMember(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/team/teams/{teamId}/members/{userId}/owner")
    boolean isTeamOwner(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/team/teams/{teamId}/members/{userId}/admin")
    boolean isTeamAdmin(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/team/teams/{teamId}/members/{userId}/manager")
    boolean isTeamManager(
            @PathVariable("teamId") Long teamId,
            @PathVariable("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );
}
