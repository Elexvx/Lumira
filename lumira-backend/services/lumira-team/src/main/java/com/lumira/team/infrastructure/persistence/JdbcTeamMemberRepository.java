package com.lumira.team.infrastructure.persistence;

import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcTeamMemberRepository implements TeamMemberRepository {
    private final MyBatisQueryOperations jdbcTemplate;

    public JdbcTeamMemberRepository(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void addOwner(Long tenantId, Long teamId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into team_member (tenant_id, team_id, user_id, role, status, joined_at, created_at, updated_at, deleted)
                        values (?, ?, ?, 'OWNER', 'ACTIVE', ?, ?, ?, 0)
                        """,
                tenantId,
                teamId,
                userId,
                now,
                now,
                now
        );
    }

    @Override
    public List<TeamVO.Member> listMembers(Long tenantId, Long teamId) {
        return jdbcTemplate.query(
                memberSelect("""
                        where tenant_id = ?
                          and team_id = ?
                          and deleted = 0
                        order by field(role, 'OWNER', 'ADMIN', 'MANAGER', 'MEMBER'), id asc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                tenantId,
                teamId
        );
    }

    @Override
    public TeamVO.Member findMemberById(Long tenantId, Long teamId, Long memberId) {
        List<TeamVO.Member> members = jdbcTemplate.query(
                memberSelect("""
                        where tenant_id = ?
                          and team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                tenantId,
                teamId,
                memberId
        );
        return members.isEmpty() ? null : members.get(0);
    }

    @Override
    public void updateMemberRole(Long tenantId, Long teamId, Long memberId, String role) {
        jdbcTemplate.update(
                "update team_member set role = ?, updated_at = ? where tenant_id = ? and team_id = ? and id = ? and deleted = 0",
                role,
                LocalDateTime.now(),
                tenantId,
                teamId,
                memberId
        );
    }

    @Override
    public void removeMember(Long tenantId, Long teamId, Long memberId) {
        jdbcTemplate.update(
                "update team_member set status = 'REMOVED', deleted = 1, updated_at = ? where tenant_id = ? and team_id = ? and id = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                teamId,
                memberId
        );
    }

    @Override
    public void transferOwner(Long tenantId, Long teamId, Long previousOwnerUserId, String previousOwnerRole, Long newOwnerMemberId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "update team_member set role = ? where tenant_id = ? and team_id = ? and user_id = ? and status = 'ACTIVE' and deleted = 0",
                previousOwnerRole,
                tenantId,
                teamId,
                previousOwnerUserId
        );
        jdbcTemplate.update(
                "update team_member set role = 'OWNER', updated_at = ? where tenant_id = ? and team_id = ? and id = ?",
                now,
                tenantId,
                teamId,
                newOwnerMemberId
        );
    }

    @Override
    public void ensureDirectMember(Long tenantId, Long teamId, Long userId, Long invitedBy, String role) {
        try {
            int updated = jdbcTemplate.update(
                    """
                            update team_member
                            set status = 'ACTIVE', role = ?, invited_by = ?, joined_at = ?, updated_at = ?, deleted = 0
                            where tenant_id = ?
                              and team_id = ?
                              and user_id = ?
                              and deleted = 1
                            """,
                    role,
                    invitedBy,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    tenantId,
                    teamId,
                    userId
            );
            if (updated == 0) {
                jdbcTemplate.update(
                        """
                                insert into team_member (tenant_id, team_id, user_id, role, status, invited_by, joined_at, created_at, updated_at, deleted)
                                values (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, 0)
                                """,
                        tenantId,
                        teamId,
                        userId,
                        role,
                        invitedBy,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );
            }
        } catch (DuplicateKeyException exception) {
            // Existing active member is an idempotent success path.
        }
    }

    @Override
    public void refreshMemberCount(Long tenantId, Long teamId) {
        jdbcTemplate.update(
                """
                        update team
                        set member_count = (
                            select count(1)
                            from team_member
                            where tenant_id = ?
                              and team_id = ?
                              and status = 'ACTIVE'
                              and deleted = 0
                        )
                        where tenant_id = ?
                          and id = ?
                        """,
                tenantId,
                teamId,
                tenantId,
                teamId
        );
    }

    @Override
    public void removeMembersByTeam(Long tenantId, Long teamId) {
        jdbcTemplate.update(
                "update team_member set deleted = 1, status = 'REMOVED', updated_at = ? where tenant_id = ? and team_id = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                teamId
        );
    }

    private String memberSelect(String tail) {
        return """
                select id, tenant_id as tenantId, team_id as teamId, user_id as userId, role,
                       member_alias as memberAlias, status, invited_by as invitedBy,
                       joined_at as joinedAt, created_at as createdAt
                from team_member
                """ + tail;
    }
}
