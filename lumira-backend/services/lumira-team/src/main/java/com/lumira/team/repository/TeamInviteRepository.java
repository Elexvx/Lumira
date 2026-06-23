package com.lumira.team.repository;

import com.lumira.team.vo.TeamVO;

import java.time.LocalDateTime;
import java.util.List;

public interface TeamInviteRepository {
    boolean existsActiveCode(Long tenantId, String inviteCode);

    Long createInvite(
            Long tenantId,
            Long teamId,
            String inviteCode,
            String tokenHash,
            String inviteType,
            String roleOnJoin,
            LocalDateTime expiresAt,
            Integer maxUses,
            boolean needApproval,
            Long createdBy
    );

    TeamVO.Invite findById(Long tenantId, Long teamId, Long inviteId);

    TeamVO.Invite findByTokenHash(String tokenHash);

    TeamVO.Invite findByCode(Long tenantId, String inviteCode);

    List<TeamVO.Invite> listInvites(Long tenantId, Long teamId);

    boolean consumeInviteQuota(TeamVO.Invite invite);

    boolean disableInvite(Long tenantId, Long teamId, Long inviteId);

    void disableInvitesByTeam(Long tenantId, Long teamId);
}
