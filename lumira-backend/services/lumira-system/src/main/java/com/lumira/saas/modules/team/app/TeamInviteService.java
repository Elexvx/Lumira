package com.lumira.saas.modules.team.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.team.dto.TeamDTO;
import com.lumira.saas.modules.team.vo.TeamVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TeamInviteService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> ROLES_ON_JOIN = Set.of("ADMIN", "MANAGER", "MEMBER");

    private final MyBatisQueryOperations jdbcTemplate;
    private final TeamAppService teamAppService;
    private final TeamPermissionService permissionService;
    private final OperationAuditService operationAuditService;

    public TeamInviteService(
            MyBatisQueryOperations jdbcTemplate,
            TeamAppService teamAppService,
            TeamPermissionService permissionService,
            OperationAuditService operationAuditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.teamAppService = teamAppService;
        this.permissionService = permissionService;
        this.operationAuditService = operationAuditService;
    }

    @Transactional
    public TeamVO.Invite createInvite(CurrentUser currentUser, Long teamId, TeamDTO.InviteCreateRequest request) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        String actorRole = permissionService.activeRole(tenantId, teamId, currentUser.getUserId());
        if (!permissionService.canInvite(actorRole)) {
            throw TeamAppService.biz(ErrorCode.FORBIDDEN, "Invite creation requires owner or admin");
        }
        TeamVO.Team team = requireActiveTeam(tenantId, teamId, currentUser.getUserId());
        String rawToken = generateRawToken();
        String hash = sha256(rawToken);
        String inviteCode = normalizeInviteCode(request.getInviteCode());
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
                hash,
                normalizeText(request.getInviteType(), "LINK"),
                teamAppService.normalizeEnum(request.getRoleOnJoin(), "MEMBER", ROLES_ON_JOIN, "Invalid role on join"),
                request.getExpiresAt(),
                normalizeMaxUses(request.getMaxUses()),
                Boolean.TRUE.equals(request.getNeedApproval()) ? 1 : 0,
                currentUser.getUserId(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        Long inviteId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        TeamVO.Invite invite = requireInvite(tenantId, teamId, inviteId);
        invite.setRawToken(rawToken);
        invite.setInviteUrl("/team/join?token=" + rawToken);
        audit(currentUser, "teamInvite", "create", "CREATE", "Created invite for " + team.getTeamName());
        return invite;
    }

    public List<TeamVO.Invite> listInvites(CurrentUser currentUser, Long teamId) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        permissionService.requireTeamAdmin(tenantId, teamId, currentUser.getUserId());
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

    @Transactional
    public boolean disableInvite(CurrentUser currentUser, Long teamId, Long inviteId) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        permissionService.requireTeamAdmin(tenantId, teamId, currentUser.getUserId());
        int updated = jdbcTemplate.update(
                "update team_invite set status = 'DISABLED', updated_at = ? where tenant_id = ? and team_id = ? and id = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                teamId,
                inviteId
        );
        if (updated == 0) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Invite not found");
        }
        return true;
    }

    public TeamVO.Invite previewByToken(String rawToken) {
        TeamVO.Invite invite = queryInviteByToken(rawToken);
        validateInviteUsable(invite);
        invite.setRawToken(null);
        invite.setInviteUrl(null);
        return invite;
    }

    @Transactional
    public TeamVO.JoinResult joinByToken(CurrentUser currentUser, String rawToken) {
        TeamVO.Invite invite = queryInviteByToken(rawToken);
        validateInviteUsable(invite);
        return joinWithInvite(currentUser, invite);
    }

    @Transactional
    public TeamVO.JoinResult joinByCode(CurrentUser currentUser, String inviteCode) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        List<TeamVO.Invite> invites = jdbcTemplate.query(
                inviteSelect("""
                        where tenant_id = ?
                          and invite_code = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                tenantId,
                normalizeInviteCode(inviteCode)
        );
        if (invites.isEmpty()) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Invite not found");
        }
        TeamVO.Invite invite = invites.get(0);
        validateInviteUsable(invite);
        return joinWithInvite(currentUser, invite);
    }

    @Transactional
    public TeamVO.JoinResult createJoinRequest(CurrentUser currentUser, Long teamId, TeamDTO.JoinRequestCreateRequest request) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        TeamVO.Team team = requireActiveTeam(tenantId, teamId, currentUser.getUserId());
        if ("PRIVATE".equals(team.getVisibility()) && !"APPLY".equals(team.getJoinMode()) && !"OPEN".equals(team.getJoinMode())) {
            throw TeamAppService.biz(ErrorCode.FORBIDDEN, "This team does not accept join requests");
        }
        if (permissionService.activeMember(tenantId, teamId, currentUser.getUserId()) != null) {
            return joinedResult(team);
        }
        return pendingResult(createPendingRequest(tenantId, teamId, currentUser.getUserId(), null, request == null ? null : request.getApplyMessage()), team);
    }

    public List<TeamVO.JoinRequest> listJoinRequests(CurrentUser currentUser, Long teamId) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        permissionService.requireTeamAdmin(tenantId, teamId, currentUser.getUserId());
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

    @Transactional
    public TeamVO.JoinRequest approveJoinRequest(CurrentUser currentUser, Long teamId, Long requestId, TeamDTO.JoinReviewRequest request) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        permissionService.requireTeamAdmin(tenantId, teamId, currentUser.getUserId());
        TeamVO.JoinRequest joinRequest = requireJoinRequest(tenantId, teamId, requestId);
        if (!"PENDING".equals(joinRequest.getStatus())) {
            throw TeamAppService.biz(ErrorCode.BIZ_ERROR, "Join request already reviewed");
        }
        teamAppService.ensureDirectMember(tenantId, teamId, joinRequest.getUserId(), currentUser.getUserId(), "MEMBER");
        jdbcTemplate.update(
                """
                        update team_join_request
                        set status = 'APPROVED', reviewed_by = ?, reviewed_at = ?, review_message = ?, updated_at = ?
                        where tenant_id = ? and team_id = ? and id = ? and status = 'PENDING' and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                request == null ? null : teamAppService.trimToNull(request.getReviewMessage()),
                LocalDateTime.now(),
                tenantId,
                teamId,
                requestId
        );
        return requireJoinRequest(tenantId, teamId, requestId);
    }

    @Transactional
    public TeamVO.JoinRequest rejectJoinRequest(CurrentUser currentUser, Long teamId, Long requestId, TeamDTO.JoinReviewRequest request) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        permissionService.requireTeamAdmin(tenantId, teamId, currentUser.getUserId());
        int updated = jdbcTemplate.update(
                """
                        update team_join_request
                        set status = 'REJECTED', reviewed_by = ?, reviewed_at = ?, review_message = ?, updated_at = ?
                        where tenant_id = ? and team_id = ? and id = ? and status = 'PENDING' and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                request == null ? null : teamAppService.trimToNull(request.getReviewMessage()),
                LocalDateTime.now(),
                tenantId,
                teamId,
                requestId
        );
        if (updated == 0) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Pending join request not found");
        }
        return requireJoinRequest(tenantId, teamId, requestId);
    }

    private TeamVO.JoinResult joinWithInvite(CurrentUser currentUser, TeamVO.Invite invite) {
        Long tenantId = teamAppService.requireTenantId(currentUser);
        if (!tenantId.equals(invite.getTenantId())) {
            throw TeamAppService.biz(ErrorCode.FORBIDDEN, "Invite is not available in current tenant");
        }
        TeamVO.Team team = requireActiveTeam(tenantId, invite.getTeamId(), currentUser.getUserId());
        if (permissionService.activeMember(tenantId, invite.getTeamId(), currentUser.getUserId()) != null) {
            return joinedResult(team);
        }
        if (Boolean.TRUE.equals(invite.getNeedApproval()) || "APPLY".equals(team.getJoinMode())) {
            return pendingResult(createPendingRequest(tenantId, invite.getTeamId(), currentUser.getUserId(), invite.getId(), null), team);
        }
        consumeInvite(invite);
        teamAppService.ensureDirectMember(tenantId, invite.getTeamId(), currentUser.getUserId(), null, invite.getRoleOnJoin());
        return joinedResult(requireActiveTeam(tenantId, invite.getTeamId(), currentUser.getUserId()));
    }

    private TeamVO.JoinRequest createPendingRequest(Long tenantId, Long teamId, Long userId, Long inviteId, String applyMessage) {
        try {
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
                    teamAppService.trimToNull(applyMessage),
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
            return requireJoinRequest(tenantId, teamId, id);
        } catch (DuplicateKeyException exception) {
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
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            throw exception;
        }
    }

    private void consumeInvite(TeamVO.Invite invite) {
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
        if (updated == 0) {
            throw TeamAppService.biz(ErrorCode.BIZ_ERROR, "Invite is no longer usable");
        }
    }

    private void validateInviteUsable(TeamVO.Invite invite) {
        if (invite == null || !"ACTIVE".equals(invite.getStatus())) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Invite not found");
        }
        if (invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw TeamAppService.biz(ErrorCode.BIZ_ERROR, "Invite has expired");
        }
        if (invite.getMaxUses() != null && invite.getUsedCount() != null && invite.getUsedCount() >= invite.getMaxUses()) {
            throw TeamAppService.biz(ErrorCode.BIZ_ERROR, "Invite usage limit reached");
        }
        requireActiveTeam(invite.getTenantId(), invite.getTeamId(), null);
    }

    private TeamVO.Invite queryInviteByToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw TeamAppService.biz(ErrorCode.VALIDATION_ERROR, "Invite token is required");
        }
        List<TeamVO.Invite> invites = jdbcTemplate.query(
                inviteSelect("""
                        where invite_token_hash = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Invite.class),
                sha256(rawToken)
        );
        return invites.isEmpty() ? null : invites.get(0);
    }

    private TeamVO.Invite requireInvite(Long tenantId, Long teamId, Long inviteId) {
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
        if (invites.isEmpty()) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Invite not found");
        }
        return invites.get(0);
    }

    private TeamVO.JoinRequest requireJoinRequest(Long tenantId, Long teamId, Long requestId) {
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
        if (rows.isEmpty()) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Join request not found");
        }
        return rows.get(0);
    }

    private TeamVO.Team requireActiveTeam(Long tenantId, Long teamId, Long currentUserId) {
        TeamVO.Team team = teamAppService.queryTeam(tenantId, teamId, currentUserId);
        if (team == null || !"ACTIVE".equals(team.getStatus())) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        return team;
    }

    private TeamVO.JoinResult joinedResult(TeamVO.Team team) {
        TeamVO.JoinResult result = new TeamVO.JoinResult();
        result.setStatus("JOINED");
        result.setTeam(team);
        return result;
    }

    private TeamVO.JoinResult pendingResult(TeamVO.JoinRequest request, TeamVO.Team team) {
        TeamVO.JoinResult result = new TeamVO.JoinResult();
        result.setStatus("PENDING");
        result.setTeam(team);
        result.setJoinRequest(request);
        return result;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeInviteCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (code.length() < 8) {
            throw TeamAppService.biz(ErrorCode.VALIDATION_ERROR, "Invite code must be at least 8 characters");
        }
        return code;
    }

    private Integer normalizeMaxUses(Integer value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw TeamAppService.biz(ErrorCode.VALIDATION_ERROR, "Max uses must be positive");
        }
        return value;
    }

    private String normalizeText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
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

    private String joinRequestSelect(String tail) {
        return """
                select id, tenant_id as tenantId, team_id as teamId, user_id as userId,
                       invite_id as inviteId, apply_message as applyMessage, status,
                       reviewed_by as reviewedBy, reviewed_at as reviewedAt,
                       review_message as reviewMessage, created_at as createdAt
                from team_join_request
                """ + tail;
    }

    private void audit(CurrentUser currentUser, String module, String action, String type, String message) {
        if (operationAuditService != null) {
            operationAuditService.log(currentUser.getCurrentTenantId(), currentUser.getUserId(), currentUser.getUsername(), module, action, type, "SUCCESS", message);
        }
    }
}
