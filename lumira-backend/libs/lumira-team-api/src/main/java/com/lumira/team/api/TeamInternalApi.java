package com.lumira.team.api;

import java.util.List;

public interface TeamInternalApi {
    TeamSummaryDTO getTeam(Long tenantId, Long teamId);

    List<TeamMemberDTO> listActiveMembers(Long tenantId, Long teamId);

    TeamMemberDTO requireActiveMember(Long tenantId, Long teamId, Long userId);

    boolean isTeamOwner(Long tenantId, Long teamId, Long userId);

    boolean isTeamAdmin(Long tenantId, Long teamId, Long userId);

    boolean isTeamManager(Long tenantId, Long teamId, Long userId);
}
