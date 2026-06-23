package com.lumira.team.repository;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;

import java.util.List;

public interface TeamMemberRepository {
    void addOwner(Long tenantId, Long teamId, Long userId);

    List<TeamVO.Member> listMembers(Long tenantId, Long teamId);

    TeamVO.Member findMemberById(Long tenantId, Long teamId, Long memberId);

    void updateMemberRole(Long tenantId, Long teamId, Long memberId, String role);

    void removeMember(Long tenantId, Long teamId, Long memberId);

    void transferOwner(Long tenantId, Long teamId, Long previousOwnerUserId, String previousOwnerRole, Long newOwnerMemberId);

    void ensureDirectMember(Long tenantId, Long teamId, Long userId, Long invitedBy, String role);

    Long addDraftMember(Long tenantId, Long teamId, TeamDTO.DraftMemberRequest request);

    void refreshMemberCount(Long tenantId, Long teamId);

    void removeMembersByTeam(Long tenantId, Long teamId);
}
