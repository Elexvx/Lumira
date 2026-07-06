package com.lumira.team.repository;

import com.lumira.team.vo.TeamVO;

import java.util.List;

public interface TeamJoinRequestRepository {
    Long createPending(Long teamId, Long userId, String userUuid, Long inviteId, String applyMessage);

    TeamVO.JoinRequest findPending(Long teamId, Long userId, String userUuid);

    TeamVO.JoinRequest findById(Long teamId, Long requestId);

    List<TeamVO.JoinRequest> listByTeam(Long teamId);

    boolean approve(
            Long teamId,
            Long requestId,
            Long requestUserId,
            String requestUserUuid,
            Long reviewedBy,
            String reviewedByUuid,
            String reviewMessage
    );

    boolean reject(
            Long teamId,
            Long requestId,
            Long requestUserId,
            String requestUserUuid,
            Long reviewedBy,
            String reviewedByUuid,
            String reviewMessage
    );

    void closeRequestsByTeam(Long teamId, TeamVO.Team expectedTeam);
}
