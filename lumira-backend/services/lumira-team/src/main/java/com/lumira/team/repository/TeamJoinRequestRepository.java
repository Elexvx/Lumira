package com.lumira.team.repository;

import com.lumira.team.vo.TeamVO;

import java.util.List;

public interface TeamJoinRequestRepository {
    Long createPending(Long teamId, Long userId, Long inviteId, String applyMessage);

    TeamVO.JoinRequest findPending(Long teamId, Long userId);

    TeamVO.JoinRequest findById(Long teamId, Long requestId);

    List<TeamVO.JoinRequest> listByTeam(Long teamId);

    boolean approve(Long teamId, Long requestId, Long reviewedBy, String reviewMessage);

    boolean reject(Long teamId, Long requestId, Long reviewedBy, String reviewMessage);

    void closeRequestsByTeam(Long teamId);
}
