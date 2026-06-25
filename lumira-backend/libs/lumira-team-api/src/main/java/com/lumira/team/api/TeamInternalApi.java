package com.lumira.team.api;

import java.util.List;

public interface TeamInternalApi {
    TeamSummaryDTO getTeam(Long teamId);

    List<TeamMemberDTO> listActiveMembers(Long teamId);

    TeamMemberDTO requireActiveMember(Long teamId, Long userId);

    boolean isTeamOwner(Long teamId, Long userId);

    boolean isTeamAdmin(Long teamId, Long userId);

    boolean isTeamManager(Long teamId, Long userId);
}
