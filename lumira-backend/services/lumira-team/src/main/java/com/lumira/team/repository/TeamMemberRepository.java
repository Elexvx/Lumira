package com.lumira.team.repository;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;

import java.util.List;

public interface TeamMemberRepository {
    void addOwner(Long teamId, Long userId, String userUuid);

    List<TeamVO.Member> listMembers(Long teamId);

    TeamVO.Member findMemberById(Long teamId, Long memberId);

    TeamVO.Member findActiveMember(Long teamId, Long userId, String userUuid);

    boolean updateMemberRole(Long teamId, TeamVO.Member expectedMember, String role);

    boolean removeMember(Long teamId, TeamVO.Member expectedMember);

    boolean transferOwner(
            Long teamId,
            Long previousOwnerUserId,
            String previousOwnerUserUuid,
            String previousOwnerRole,
            Long newOwnerMemberId,
            Long newOwnerUserId,
            String newOwnerUserUuid
    );

    void ensureDirectMember(Long teamId, Long userId, String userUuid, Long invitedBy, String invitedByUuid, String role);

    Long addDraftMember(Long teamId, TeamDTO.DraftMemberRequest request);

    void refreshMemberCount(Long teamId, TeamVO.Team expectedTeam);

    void removeMembersByTeam(Long teamId, TeamVO.Team expectedTeam);
}
