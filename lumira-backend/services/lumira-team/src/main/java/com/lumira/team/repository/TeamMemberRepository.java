package com.lumira.team.repository;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;

import java.util.List;

public interface TeamMemberRepository {
    void addOwner(Long teamId, Long userId);

    List<TeamVO.Member> listMembers(Long teamId);

    TeamVO.Member findMemberById(Long teamId, Long memberId);

    void updateMemberRole(Long teamId, Long memberId, String role);

    void removeMember(Long teamId, Long memberId);

    void transferOwner(Long teamId, Long previousOwnerUserId, String previousOwnerRole, Long newOwnerMemberId);

    void ensureDirectMember(Long teamId, Long userId, Long invitedBy, String role);

    Long addDraftMember(Long teamId, TeamDTO.DraftMemberRequest request);

    void refreshMemberCount(Long teamId);

    void removeMembersByTeam(Long teamId);
}
