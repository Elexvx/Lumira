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
    public boolean existsActiveCode(Long tenantId, String inviteCode) {
        return jdbcTemplate.exists(
                "select 1 from team_invite where tenant_id = ? and invite_code = ? and status = 'ACTIVE' and deleted = 0 limit 1",
                tenantId,
                inviteCode
        );
    }

    @Override
    public Long createInvite(
            Long tenantId,
            Long teamId,
            String inviteCode,
            String tokenHash,
            String inviteType,
            String roleOnJoin,
            LocalDateTime expiresAt,
            Integer maxUses,
            boolean needApproval,
            Long createdBy
    ) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into team_invite (
                            tenant_id, team_id, invite_code, invite_token_hash, invite_type,
                            role_on_join, expires_at, max_uses, used_count, need_approval,
                            status, created_by, created_at, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'ACTIVE', ?, ?, ?, 0)
                        """,
                tenantId,
                teamId,
                inviteCode,
                tokenHash,
                inviteType,
                roleOnJoin,
                expiresAt,
                maxUses,
                needApproval ? 1 : 0,
                createdBy,
                now,
                now
        );
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public TeamVO.Invite findById(Long tenantId, Long teamId, Long inviteId) {
        List<TeamVO.Invite> invites = jdbcTemplate.query(
                inviteSelect("""
                        where tenant_id = ?
                          and team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                tenantId,
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
    public TeamVO.Invite findByCode(Long tenantId, String inviteCode) {
        List<TeamVO.Invite> invites = jdbcTemplate.query(
                inviteSelect("""
                        where tenant_id = ?
                          and invite_code = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                tenantId,
                inviteCode
        );
        return invites.isEmpty() ? null : invites.get(0);
    }

    @Override
    public List<TeamVO.Invite> listInvites(Long tenantId, Long teamId) {
        return jdbcTemplate.query(
                inviteSelect("""
                        where tenant_id = ?
                          and team_id = ?
                          and deleted = 0
                        order by created_at desc, id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                tenantId,
                teamId
        );
    }

    @Override
    public boolean consumeInviteQuota(TeamVO.Invite invite) {
        int updated = jdbcTemplate.update(
                """
                        update team_invite
                        set used_count = used_count + 1, updated_at = ?
                        where tenant_id = ?
                          and id = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                          and (expires_at is null or expires_at > ?)
                          and (max_uses is null or used_count < max_uses)
                        """,
                LocalDateTime.now(),
                invite.getTenantId(),
                invite.getId(),
                LocalDateTime.now()
        );
        return updated > 0;
    }

    @Override
    public boolean disableInvite(Long tenantId, Long teamId, Long inviteId) {
        int updated = jdbcTemplate.update(
                "update team_invite set status = 'DISABLED', updated_at = ? where tenant_id = ? and team_id = ? and id = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                teamId,
                inviteId
        );
        return updated > 0;
    }

    @Override
    public void disableInvitesByTeam(Long tenantId, Long teamId) {
        jdbcTemplate.update(
                "update team_invite set deleted = 1, status = 'DISABLED', updated_at = ? where tenant_id = ? and team_id = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                teamId
        );
    }

    private String inviteSelect(String tail) {
        return """
                select id, tenant_id as tenantId, team_id as teamId, invite_code as inviteCode,
                       invite_type as inviteType, role_on_join as roleOnJoin, expires_at as expiresAt,
                       max_uses as maxUses, used_count as usedCount,
                       case when need_approval = 1 then true else false end as needApproval,
                       status, created_at as createdAt
                from team_invite
                """ + tail;
    }
}
