package com.lumira.team.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
public class TeamPermissionService {
    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String MEMBER = "MEMBER";

    private final TeamMemberRepository memberRepository;

    public TeamPermissionService(TeamMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public void requireTeamOwner(Long teamId, Long userId, String userUuid) {
        requireAnyRole(teamId, userId, userUuid, Set.of(OWNER), "Team owner permission required");
    }

    public void requireTeamAdmin(Long teamId, Long userId, String userUuid) {
        requireAnyRole(teamId, userId, userUuid, Set.of(OWNER, ADMIN), "Team admin permission required");
    }

    public void requireTeamManager(Long teamId, Long userId, String userUuid) {
        requireAnyRole(teamId, userId, userUuid, Set.of(OWNER, ADMIN, MANAGER), "Team manager permission required");
    }

    public void requireTeamMember(Long teamId, Long userId, String userUuid) {
        requireAnyRole(teamId, userId, userUuid, Set.of(OWNER, ADMIN, MANAGER, MEMBER), "Team membership required");
    }

    public boolean canInvite(String role) {
        return OWNER.equals(role) || ADMIN.equals(role);
    }

    public boolean canUpdateTeam(String role) {
        return OWNER.equals(role) || ADMIN.equals(role) || MANAGER.equals(role);
    }

    public boolean canDisbandTeam(String role) {
        return OWNER.equals(role);
    }

    public boolean canRemoveMember(String actorRole, String targetRole, boolean removingSelf) {
        if (OWNER.equals(targetRole)) {
            return false;
        }
        if (removingSelf) {
            return OWNER.equals(actorRole) ? false : Set.of(ADMIN, MANAGER, MEMBER).contains(actorRole);
        }
        if (OWNER.equals(actorRole)) {
            return true;
        }
        if (ADMIN.equals(actorRole)) {
            return MANAGER.equals(targetRole) || MEMBER.equals(targetRole);
        }
        if (MANAGER.equals(actorRole)) {
            return MEMBER.equals(targetRole);
        }
        return false;
    }

    public TeamVO.Member activeMember(Long teamId, Long userId, String userUuid) {
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(userId, "User id is required");
        requireUserUuid(userUuid);
        return memberRepository.findActiveMember(teamId, userId, userUuid.trim());
    }

    public TeamVO.Member memberById(Long teamId, Long memberId) {
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(memberId, "Team member id is required");
        return memberRepository.findMemberById(teamId, memberId);
    }

    public String activeRole(Long teamId, Long userId, String userUuid) {
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(userId, "User id is required");
        TeamVO.Member member = activeMember(teamId, userId, userUuid);
        return member == null ? null : member.getRole();
    }

    private void requireAnyRole(Long teamId, Long userId, String userUuid, Set<String> allowedRoles, String message) {
        requirePositiveId(teamId, "Team id is required");
        requirePositiveId(userId, "User id is required");
        String role = activeRole(teamId, userId, userUuid);
        if (!allowedRoles.contains(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, message, message);
        }
    }

    private void requireUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User uuid is required", "User uuid is required");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message, message);
        }
    }
}
