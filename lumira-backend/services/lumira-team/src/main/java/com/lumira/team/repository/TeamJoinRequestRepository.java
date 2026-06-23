package com.lumira.team.repository;

import com.lumira.team.vo.TeamVO;

import java.util.List;

public interface TeamJoinRequestRepository {
    Long createPending(Long tenantId, Long teamId, Long userId, Long inviteId, String applyMessage);

    TeamVO.JoinRequest findPending(Long tenantId, Long teamId, Long userId);

    TeamVO.JoinRequest findById(Long tenantId, Long teamId, Long requestId);

    List<TeamVO.JoinRequest> listByTeam(Long tenantId, Long teamId);

    boolean approve(Long tenantId, Long teamId, Long requestId, Long reviewedBy, String reviewMessage);

    boolean reject(Long tenantId, Long teamId, Long requestId, Long reviewedBy, String reviewMessage);

    void closeRequestsByTeam(Long tenantId, Long teamId);
}
