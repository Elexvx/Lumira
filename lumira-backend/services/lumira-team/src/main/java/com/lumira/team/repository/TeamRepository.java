package com.lumira.team.repository;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;

import java.util.List;
import java.util.Set;

public interface TeamRepository {
    String nextTeamCode(Long tenantId);

    Long createTeam(Long tenantId, String teamCode, Long ownerUserId, TeamDTO.TeamCreateRequest request);

    List<TeamVO.Team> listMyTeams(Long tenantId, Long userId);

    List<TeamVO.Team> listTeamsForAdmin(Long tenantId, Long userId);

    TeamVO.Team findTeam(Long tenantId, Long teamId, Long currentUserId);

    int updateTeamProfile(Long tenantId, Long teamId, Long updatedBy, TeamDTO.TeamUpdateRequest request);

    int softDeleteTeam(Long tenantId, Long teamId, Long updatedBy);

    int transferOwner(Long tenantId, Long teamId, Long newOwnerUserId, Long updatedBy);

    Set<String> loadEnabledDictValues(Long tenantId, String dictCode);
}
