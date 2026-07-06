package com.lumira.team.repository;

import com.lumira.team.vo.TeamVO;

import java.time.LocalDateTime;
import java.util.List;

public interface TeamInviteRepository {
    boolean existsActiveCode(String inviteCode);

    Long createInvite(
            Long teamId,
            String inviteCode,
            String tokenHash,
            String inviteType,
            String roleOnJoin,
            LocalDateTime expiresAt,
            Integer maxUses,
            boolean needApproval,
            Long createdBy,
            String createdByUuid
    );

    TeamVO.Invite findById(Long teamId, Long inviteId);

    TeamVO.Invite findByTokenHash(String tokenHash);

    TeamVO.Invite findByCode(String inviteCode);

    List<TeamVO.Invite> listInvites(Long teamId);

    boolean consumeInviteQuota(TeamVO.Invite invite, Long updatedBy, String updatedByUuid);

    boolean disableInvite(Long teamId, Long inviteId, Long updatedBy, String updatedByUuid);

    void disableInvitesByTeam(Long teamId, TeamVO.Team expectedTeam, Long updatedBy, String updatedByUuid);
}
