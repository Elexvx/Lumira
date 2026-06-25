package com.lumira.team.repository;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;

import java.util.List;
import java.util.Set;

public interface TeamRepository {
    String nextTeamCode();

    Long createTeam(String teamCode, Long ownerUserId, TeamDTO.TeamCreateRequest request);

    List<TeamVO.Team> listMyTeams(Long userId);

    List<TeamVO.Team> listTeamsForAdmin(Long userId);

    TeamVO.Team findTeam(Long teamId, Long currentUserId);

    int updateTeamProfile(Long teamId, Long updatedBy, TeamDTO.TeamUpdateRequest request);

    int softDeleteTeam(Long teamId, Long updatedBy);

    int transferOwner(Long teamId, Long newOwnerUserId, Long updatedBy);

    Set<String> loadEnabledDictValues(String dictCode);
}
