package com.lumira.team.infrastructure.persistence;

import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcTeamJoinRequestRepository implements TeamJoinRequestRepository {
    private final MyBatisQueryOperations jdbcTemplate;

    public JdbcTeamJoinRequestRepository(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long createPending(Long tenantId, Long teamId, Long userId, Long inviteId, String applyMessage) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into team_join_request (
                            tenant_id, team_id, user_id, invite_id, apply_message, status, created_at, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, 'PENDING', ?, ?, 0)
                        """,
                tenantId,
                teamId,
                userId,
                inviteId,
                applyMessage,
                now,
                now
        );
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public TeamVO.JoinRequest findPending(Long tenantId, Long teamId, Long userId) {
        List<TeamVO.JoinRequest> rows = jdbcTemplate.query(
                joinRequestSelect("""
                        where tenant_id = ?
                          and team_id = ?
                          and user_id = ?
                          and status = 'PENDING'
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.JoinRequest.class),
                tenantId,
                teamId,
                userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public TeamVO.JoinRequest findById(Long tenantId, Long teamId, Long requestId) {
        List<TeamVO.JoinRequest> rows = jdbcTemplate.query(
                joinRequestSelect("""
                        where tenant_id = ?
                          and team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.JoinRequest.class),
                tenantId,
                teamId,
                requestId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<TeamVO.JoinRequest> listByTeam(Long tenantId, Long teamId) {
        return jdbcTemplate.query(
                joinRequestSelect("""
                        where tenant_id = ?
                          and team_id = ?
                          and deleted = 0
                        order by created_at desc, id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.JoinRequest.class),
                tenantId,
                teamId
        );
    }

    @Override
    public boolean approve(Long tenantId, Long teamId, Long requestId, Long reviewedBy, String reviewMessage) {
        int updated = jdbcTemplate.update(
                """
                        update team_join_request
                        set status = 'APPROVED', reviewed_by = ?, reviewed_at = ?, review_message = ?, updated_at = ?
                        where tenant_id = ? and team_id = ? and id = ? and status = 'PENDING' and deleted = 0
                        """,
                reviewedBy,
                LocalDateTime.now(),
                reviewMessage,
                LocalDateTime.now(),
                tenantId,
                teamId,
                requestId
        );
        return updated > 0;
    }

    @Override
    public boolean reject(Long tenantId, Long teamId, Long requestId, Long reviewedBy, String reviewMessage) {
        int updated = jdbcTemplate.update(
                """
                        update team_join_request
                        set status = 'REJECTED', reviewed_by = ?, reviewed_at = ?, review_message = ?, updated_at = ?
                        where tenant_id = ? and team_id = ? and id = ? and status = 'PENDING' and deleted = 0
                        """,
                reviewedBy,
                LocalDateTime.now(),
                reviewMessage,
                LocalDateTime.now(),
                tenantId,
                teamId,
                requestId
        );
        return updated > 0;
    }

    @Override
    public void closeRequestsByTeam(Long tenantId, Long teamId) {
        jdbcTemplate.update(
                "update team_join_request set deleted = 1, status = 'CLOSED', updated_at = ? where tenant_id = ? and team_id = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                teamId
        );
    }

    private String joinRequestSelect(String tail) {
        return """
                select id, tenant_id as tenantId, team_id as teamId, user_id as userId,
                       invite_id as inviteId, apply_message as applyMessage, status,
                       reviewed_by as reviewedBy, reviewed_at as reviewedAt,
                       review_message as reviewMessage, created_at as createdAt
                from team_join_request
                """ + tail;
    }
}
