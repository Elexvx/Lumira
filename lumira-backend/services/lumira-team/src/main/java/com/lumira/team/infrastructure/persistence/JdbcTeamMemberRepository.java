package com.lumira.team.infrastructure.persistence;

import com.lumira.team.dto.TeamDTO;
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
    public void addOwner(Long teamId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into team_member (team_id, user_id, role, member_name, member_source, status, joined_at, created_at, updated_at, deleted)
                        values (?, ?, 'OWNER', 'Owner', 'REGISTERED', 'ACTIVE', ?, ?, ?, 0)
                        """,
                teamId,
                userId,
                now,
                now,
                now
        );
    }

    @Override
    public List<TeamVO.Member> listMembers(Long teamId) {
        return jdbcTemplate.query(
                memberSelect("""
                        where team_id = ?
                          and deleted = 0
                        order by field(role, 'OWNER', 'ADMIN', 'MANAGER', 'MEMBER'), id asc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                teamId
        );
    }

    @Override
    public TeamVO.Member findMemberById(Long teamId, Long memberId) {
        List<TeamVO.Member> members = jdbcTemplate.query(
                memberSelect("""
                        where team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                teamId,
                memberId
        );
        return members.isEmpty() ? null : members.get(0);
    }

    @Override
    public void updateMemberRole(Long teamId, Long memberId, String role) {
        jdbcTemplate.update(
                "update team_member set role = ?, updated_at = ? where team_id = ? and id = ? and deleted = 0",
                role,
                LocalDateTime.now(),
                teamId,
                memberId
        );
    }

    @Override
    public void removeMember(Long teamId, Long memberId) {
        jdbcTemplate.update(
                "update team_member set status = 'REMOVED', deleted = 1, updated_at = ? where team_id = ? and id = ? and deleted = 0",
                LocalDateTime.now(),
                teamId,
                memberId
        );
    }

    @Override
    public void transferOwner(Long teamId, Long previousOwnerUserId, String previousOwnerRole, Long newOwnerMemberId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "update team_member set role = ? where team_id = ? and user_id = ? and status = 'ACTIVE' and deleted = 0",
                previousOwnerRole,
                teamId,
                previousOwnerUserId
        );
        jdbcTemplate.update(
                "update team_member set role = 'OWNER', updated_at = ? where team_id = ? and id = ?",
                now,
                teamId,
                newOwnerMemberId
        );
    }

    @Override
    public void ensureDirectMember(Long teamId, Long userId, Long invitedBy, String role) {
        try {
            int updated = jdbcTemplate.update(
                    """
                            update team_member
                            set status = 'ACTIVE', role = ?, invited_by = ?, joined_at = ?, updated_at = ?, deleted = 0
                            where team_id = ?
                              and user_id = ?
                              and deleted = 1
                            """,
                    role,
                    invitedBy,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    teamId,
                    userId
            );
            if (updated == 0) {
                jdbcTemplate.update(
                        """
                                insert into team_member (team_id, user_id, role, member_source, status, invited_by, joined_at, created_at, updated_at, deleted)
                                values (?, ?, ?, 'REGISTERED', 'ACTIVE', ?, ?, ?, ?, 0)
                                """,
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
    public Long addDraftMember(Long teamId, TeamDTO.DraftMemberRequest request) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into team_member (
                            team_id, user_id, role, member_alias, member_name,
                            employee_no, department_name, remark, member_source, status,
                            joined_at, created_at, updated_at, deleted
                        ) values (?, null, ?, ?, ?, ?, ?, ?, 'DRAFT', 'ACTIVE', ?, ?, ?, 0)
                        """,
                teamId,
                request.getRole(),
                request.getMemberName(),
                request.getMemberName(),
                request.getEmployeeNo(),
                request.getDepartmentName(),
                request.getRemark(),
                now,
                now,
                now
        );
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public void refreshMemberCount(Long teamId) {
        jdbcTemplate.update(
                """
                        update team
                        set member_count = (
                            select count(1)
                            from team_member
                            where team_id = ?
                              and status = 'ACTIVE'
                              and deleted = 0
                        )
                        where id = ?
                        """,
                teamId,
                teamId
        );
    }

    @Override
    public void removeMembersByTeam(Long teamId) {
        jdbcTemplate.update(
                "update team_member set deleted = 1, status = 'REMOVED', updated_at = ? where team_id = ? and deleted = 0",
                LocalDateTime.now(),
                teamId
        );
    }

    private String memberSelect(String tail) {
        return """
                select id, team_id as teamId, user_id as userId, role,
                       member_alias as memberAlias, member_name as memberName,
                       employee_no as employeeNo, department_name as departmentName,
                       remark, member_source as memberSource, status, invited_by as invitedBy,
                       joined_at as joinedAt, created_at as createdAt
                from team_member
                """ + tail;
    }
}
