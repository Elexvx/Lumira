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
    public Long createPending(Long teamId, Long userId, String userUuid, Long inviteId, String applyMessage) {
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
                """
                        insert into team_join_request (
                            team_id, user_id, user_uuid, invite_id, apply_message, status, created_at, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, 'PENDING', ?, ?, 0)
                        """,
                teamId,
                userId,
                userUuid,
                inviteId,
                applyMessage,
                now,
                now
        );
        requireSingleWrite(inserted, "Team join request changed, please retry");
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public TeamVO.JoinRequest findPending(Long teamId, Long userId, String userUuid) {
        List<TeamVO.JoinRequest> rows = jdbcTemplate.query(
                joinRequestSelect("""
                        where team_id = ?
                          and user_id = ?
                          and user_uuid = ?
                          and status = 'PENDING'
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.JoinRequest.class),
                teamId,
                userId,
                userUuid
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public TeamVO.JoinRequest findById(Long teamId, Long requestId) {
        List<TeamVO.JoinRequest> rows = jdbcTemplate.query(
                joinRequestSelect("""
                        where team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.JoinRequest.class),
                teamId,
                requestId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<TeamVO.JoinRequest> listByTeam(Long teamId) {
        return jdbcTemplate.query(
                joinRequestSelect("""
                        where team_id = ?
                          and deleted = 0
                        order by created_at desc, id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.JoinRequest.class),
                teamId
        );
    }

    @Override
    public boolean approve(
            Long teamId,
            Long requestId,
            Long requestUserId,
            String requestUserUuid,
            Long reviewedBy,
            String reviewedByUuid,
            String reviewMessage
    ) {
        int updated = jdbcTemplate.update(
                """
                        update team_join_request
                        set status = 'APPROVED', reviewed_by = ?, reviewed_by_uuid = ?, reviewed_at = ?, review_message = ?, updated_at = ?
                        where team_id = ? and id = ? and user_id = ? and user_uuid = ? and status = 'PENDING' and deleted = 0
                        """,
                reviewedBy,
                reviewedByUuid,
                LocalDateTime.now(),
                reviewMessage,
                LocalDateTime.now(),
                teamId,
                requestId,
                requestUserId,
                requestUserUuid
        );
        return updated > 0;
    }

    @Override
    public boolean reject(
            Long teamId,
            Long requestId,
            Long requestUserId,
            String requestUserUuid,
            Long reviewedBy,
            String reviewedByUuid,
            String reviewMessage
    ) {
        int updated = jdbcTemplate.update(
                """
                        update team_join_request
                        set status = 'REJECTED', reviewed_by = ?, reviewed_by_uuid = ?, reviewed_at = ?, review_message = ?, updated_at = ?
                        where team_id = ? and id = ? and user_id = ? and user_uuid = ? and status = 'PENDING' and deleted = 0
                        """,
                reviewedBy,
                reviewedByUuid,
                LocalDateTime.now(),
                reviewMessage,
                LocalDateTime.now(),
                teamId,
                requestId,
                requestUserId,
                requestUserUuid
        );
        return updated > 0;
    }

    @Override
    public void closeRequestsByTeam(Long teamId, TeamVO.Team expectedTeam) {
        requireExpectedTeam(expectedTeam);
        jdbcTemplate.update(
                """
                        update team_join_request
                        set deleted = 1, status = 'CLOSED', updated_at = ?
                        where team_id = ?
                          and deleted = 0
                          and exists (
                              select 1
                              from team t
                              where t.id = team_join_request.team_id
                                and t.owner_user_id = ?
                                and t.owner_user_uuid = ?
                                and t.status = 'DELETED'
                                and t.deleted = 1
                          )
                        """,
                LocalDateTime.now(),
                teamId,
                expectedTeam.getOwnerUserId(),
                requireUserUuid(expectedTeam.getOwnerUserUuid())
        );
    }

    private void requireExpectedTeam(TeamVO.Team team) {
        if (team == null || team.getId() == null || team.getId() <= 0) {
            throw new IllegalArgumentException("Team id is required");
        }
        if (team.getOwnerUserId() == null || team.getOwnerUserId() <= 0 || team.getOwnerUserUuid() == null || team.getOwnerUserUuid().isBlank()) {
            throw new IllegalArgumentException("Team owner identity is required");
        }
    }

    private String requireUserUuid(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            throw new IllegalArgumentException("User uuid is required");
        }
        return userUuid.trim();
    }

    private void requireSingleWrite(int updated, String message) {
        if (updated != 1) {
            throw new IllegalStateException(message);
        }
    }

    private String joinRequestSelect(String tail) {
        return """
                select id, team_id as teamId, user_id as userId, user_uuid as userUuid,
                       invite_id as inviteId, apply_message as applyMessage, status,
                       reviewed_by as reviewedBy, reviewed_by_uuid as reviewedByUuid, reviewed_at as reviewedAt,
                       review_message as reviewMessage, created_at as createdAt
                from team_join_request
                """ + tail;
    }
}
