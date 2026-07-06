package com.lumira.team.repository;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;

import java.util.List;
import java.util.Set;

public interface TeamRepository {
    String nextTeamCode();

    Long createTeam(String teamCode, Long ownerUserId, String ownerUserUuid, TeamDTO.TeamCreateRequest request);

    List<TeamVO.Team> listMyTeams(Long userId, String userUuid);

    List<TeamVO.Team> listTeamsForAdmin(Long userId, String userUuid);

    TeamVO.Team findTeam(Long teamId, Long currentUserId, String currentUserUuid);

    int updateTeamProfile(Long teamId, TeamVO.Team expectedTeam, Long updatedBy, String updatedByUuid, TeamDTO.TeamUpdateRequest request);

    int softDeleteTeam(Long teamId, TeamVO.Team expectedTeam, Long updatedBy, String updatedByUuid);

    int transferOwner(Long teamId, Long currentOwnerUserId, String currentOwnerUserUuid, Long newOwnerUserId, String newOwnerUserUuid, Long updatedBy, String updatedByUuid);

    Set<String> loadEnabledDictValues(String dictCode);
}
