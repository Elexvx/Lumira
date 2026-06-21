package com.lumira.saas.modules.team.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.team.vo.TeamVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TeamPermissionService {
    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String MEMBER = "MEMBER";

    private final MyBatisQueryOperations jdbcTemplate;

    public TeamPermissionService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireTeamOwner(Long tenantId, Long teamId, Long userId) {
        requireAnyRole(tenantId, teamId, userId, Set.of(OWNER), "Team owner permission required");
    }

    public void requireTeamAdmin(Long tenantId, Long teamId, Long userId) {
        requireAnyRole(tenantId, teamId, userId, Set.of(OWNER, ADMIN), "Team admin permission required");
    }

    public void requireTeamManager(Long tenantId, Long teamId, Long userId) {
        requireAnyRole(tenantId, teamId, userId, Set.of(OWNER, ADMIN, MANAGER), "Team manager permission required");
    }

    public void requireTeamMember(Long tenantId, Long teamId, Long userId) {
        requireAnyRole(tenantId, teamId, userId, Set.of(OWNER, ADMIN, MANAGER, MEMBER), "Team membership required");
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
        if (OWNER.equals(actorRole)) {
            return true;
        }
        if (ADMIN.equals(actorRole)) {
            return !OWNER.equals(targetRole);
        }
        if (MANAGER.equals(actorRole)) {
            return MEMBER.equals(targetRole);
        }
        return removingSelf && MEMBER.equals(actorRole);
    }

    public TeamVO.Member activeMember(Long tenantId, Long teamId, Long userId) {
        List<TeamVO.Member> members = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, team_id as teamId, user_id as userId, role,
                               member_alias as memberAlias, status, invited_by as invitedBy,
                               joined_at as joinedAt, created_at as createdAt
                        from team_member
                        where tenant_id = ?
                          and team_id = ?
                          and user_id = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                tenantId,
                teamId,
                userId
        );
        return members.isEmpty() ? null : members.get(0);
    }

    public TeamVO.Member memberById(Long tenantId, Long teamId, Long memberId) {
        List<TeamVO.Member> members = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, team_id as teamId, user_id as userId, role,
                               member_alias as memberAlias, status, invited_by as invitedBy,
                               joined_at as joinedAt, created_at as createdAt
                        from team_member
                        where tenant_id = ?
                          and team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                tenantId,
                teamId,
                memberId
        );
        return members.isEmpty() ? null : members.get(0);
    }

    public String activeRole(Long tenantId, Long teamId, Long userId) {
        TeamVO.Member member = activeMember(tenantId, teamId, userId);
        return member == null ? null : member.getRole();
    }

    private void requireAnyRole(Long tenantId, Long teamId, Long userId, Set<String> allowedRoles, String message) {
        String role = activeRole(tenantId, teamId, userId);
        if (!allowedRoles.contains(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, message, message);
        }
    }
}
