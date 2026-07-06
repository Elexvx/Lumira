package com.lumira.team.infrastructure.persistence;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcTeamInviteRepository implements TeamInviteRepository {
    private final MyBatisQueryOperations jdbcTemplate;

    public JdbcTeamInviteRepository(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsActiveCode(String inviteCode) {
        return jdbcTemplate.exists(
                "select 1 from team_invite where invite_code = ? and status = 'ACTIVE' and deleted = 0 limit 1",
                inviteCode
        );
    }

    @Override
    public Long createInvite(
            Long teamId,
            String inviteCode,
            String tokenHash,
            String inviteType,
            String roleOnJoin,
            LocalDateTime expiresAt,
            Integer maxUses,
            boolean needApproval,
            Long createdBy,
            String createdByUuid
    ) {
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
                """
                        insert into team_invite (
                            team_id, invite_code, invite_token_hash, invite_type,
                            role_on_join, expires_at, max_uses, used_count, need_approval,
                            status, created_by, created_by_uuid, created_at, updated_by, updated_by_uuid, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 0, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, 0)
                        """,
                teamId,
                inviteCode,
                tokenHash,
                inviteType,
                roleOnJoin,
                expiresAt,
                maxUses,
                needApproval ? 1 : 0,
                createdBy,
                createdByUuid,
                now,
                createdBy,
                createdByUuid,
                now
        );
        requireSingleWrite(inserted, "Team invite changed, please retry");
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public TeamVO.Invite findById(Long teamId, Long inviteId) {
        List<TeamVO.Invite> invites = jdbcTemplate.query(
                inviteSelect("""
                        where team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                teamId,
                inviteId
        );
        return invites.isEmpty() ? null : invites.get(0);
    }

    @Override
    public TeamVO.Invite findByTokenHash(String tokenHash) {
        List<TeamVO.Invite> invites = jdbcTemplate.query(
                inviteSelect("""
                        where invite_token_hash = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                tokenHash
        );
        return invites.isEmpty() ? null : invites.get(0);
    }

    @Override
    public TeamVO.Invite findByCode(String inviteCode) {
        List<TeamVO.Invite> invites = jdbcTemplate.query(
                inviteSelect("""
                        where invite_code = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                inviteCode
        );
        return invites.isEmpty() ? null : invites.get(0);
    }

    @Override
    public List<TeamVO.Invite> listInvites(Long teamId) {
        return jdbcTemplate.query(
                inviteSelect("""
                        where team_id = ?
                          and deleted = 0
                        order by created_at desc, id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                teamId
        );
    }

    @Override
    public boolean consumeInviteQuota(TeamVO.Invite invite, Long updatedBy, String updatedByUuid) {
        if (invite == null || invite.getId() == null || invite.getId() <= 0 || invite.getTeamId() == null || invite.getTeamId() <= 0) {
            throw new IllegalArgumentException("Invite id and team id are required");
        }
        int updated = jdbcTemplate.update(
                """
                        update team_invite
                        set used_count = used_count + 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and team_id = ?
                          and invite_code = ?
                          and invite_type = ?
                          and role_on_join = ?
                          and need_approval = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                          and (expires_at is null or expires_at > ?)
                          and (max_uses is null or used_count < max_uses)
                """,
                updatedBy,
                requireUserUuid(updatedByUuid),
                LocalDateTime.now(),
                invite.getId(),
                invite.getTeamId(),
                invite.getInviteCode(),
                invite.getInviteType(),
                invite.getRoleOnJoin(),
                Boolean.TRUE.equals(invite.getNeedApproval()) ? 1 : 0,
                LocalDateTime.now()
        );
        return updated > 0;
    }

    @Override
    public boolean disableInvite(Long teamId, Long inviteId, Long updatedBy, String updatedByUuid) {
        TeamVO.Invite existing = findById(teamId, inviteId);
        if (existing == null) {
            return false;
        }
        int updated = jdbcTemplate.update(
                """
                        update team_invite
                        set status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where team_id = ?
                          and id = ?
                          and invite_code = ?
                          and invite_type = ?
                          and role_on_join = ?
                          and status = ?
                          and deleted = 0
                        """,
                updatedBy,
                requireUserUuid(updatedByUuid),
                LocalDateTime.now(),
                teamId,
                inviteId,
                existing.getInviteCode(),
                existing.getInviteType(),
                existing.getRoleOnJoin(),
                existing.getStatus()
        );
        return updated > 0;
    }

    @Override
    public void disableInvitesByTeam(Long teamId, TeamVO.Team expectedTeam, Long updatedBy, String updatedByUuid) {
        requireExpectedTeam(expectedTeam);
        jdbcTemplate.update(
                """
                        update team_invite
                        set deleted = 1, status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where team_id = ?
                          and deleted = 0
                          and exists (
                              select 1
                              from team t
                              where t.id = team_invite.team_id
                                and t.owner_user_id = ?
                                and t.owner_user_uuid = ?
                                and t.status = 'DELETED'
                                and t.deleted = 1
                          )
                        """,
                updatedBy,
                requireUserUuid(updatedByUuid),
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

    private String inviteSelect(String tail) {
        return """
                select id, team_id as teamId, invite_code as inviteCode,
                       invite_type as inviteType, role_on_join as roleOnJoin, expires_at as expiresAt,
                       max_uses as maxUses, used_count as usedCount,
                       case when need_approval = 1 then true else false end as needApproval,
                       status, created_at as createdAt
                from team_invite
                """ + tail;
    }
}
