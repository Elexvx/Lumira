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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TeamAppService {
    private static final Set<String> TEAM_TYPES = Set.of("GENERAL", "DEV", "COMPETITION", "CLUB", "OTHER");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "PUBLIC");
    private static final Set<String> JOIN_MODES = Set.of("INVITE_ONLY", "APPLY", "OPEN");
    private static final Set<String> MEMBER_ROLES = Set.of("OWNER", "ADMIN", "MANAGER", "MEMBER");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MyBatisQueryOperations jdbcTemplate;
    private final TeamPermissionService permissionService;
    private final OperationAuditService operationAuditService;

    public TeamAppService(
            MyBatisQueryOperations jdbcTemplate,
            TeamPermissionService permissionService,
            OperationAuditService operationAuditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.operationAuditService = operationAuditService;
    }

    @Transactional
    public TeamVO.Team createTeam(CurrentUser currentUser, TeamDTO.TeamCreateRequest request) {
        Long tenantId = requireTenantId(currentUser);
        Long userId = requireUserId(currentUser);
        String code = nextTeamCode(tenantId);
        jdbcTemplate.update(
                """
                        insert into team (
                            tenant_id, team_code, team_name, team_type, avatar_url, description,
                            visibility, join_mode, owner_user_id, member_count, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'ACTIVE', ?, ?, 0)
                        """,
                tenantId,
                code,
                trimRequired(request.getTeamName(), "Team name is required"),
                normalizeEnum(request.getTeamType(), "GENERAL", TEAM_TYPES, "Invalid team type"),
                trimToNull(request.getAvatarUrl()),
                trimToNull(request.getDescription()),
                normalizeEnum(request.getVisibility(), "PRIVATE", VISIBILITIES, "Invalid team visibility"),
                normalizeEnum(request.getJoinMode(), "INVITE_ONLY", JOIN_MODES, "Invalid join mode"),
                userId,
                userId,
                userId
        );
        Long teamId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update(
                """
                        insert into team_member (tenant_id, team_id, user_id, role, status, joined_at, created_at, updated_at, deleted)
                        values (?, ?, ?, 'OWNER', 'ACTIVE', ?, ?, ?, 0)
                        """,
                tenantId,
                teamId,
                userId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        audit(currentUser, "team", "create", "CREATE", "Created team " + request.getTeamName());
        return getTeam(currentUser, teamId);
    }

    public List<TeamVO.Team> myTeams(CurrentUser currentUser) {
        Long tenantId = requireTenantId(currentUser);
        return jdbcTemplate.query(
                teamSelect("""
                        join team_member m on m.team_id = t.id
                         and m.tenant_id = t.tenant_id
                         and m.user_id = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.tenant_id = ?
                          and t.deleted = 0
                          and t.status = 'ACTIVE'
                        order by t.updated_at desc, t.id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                currentUser.getUserId(),
                tenantId
        );
    }

    public TeamVO.Team getTeam(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        TeamVO.Team team = queryTeam(tenantId, teamId, currentUser.getUserId());
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        if ("PRIVATE".equals(team.getVisibility()) && team.getMyRole() == null) {
            throw biz(ErrorCode.FORBIDDEN, "Private team details require membership");
        }
        return team;
    }

    @Transactional
    public TeamVO.Team updateTeam(CurrentUser currentUser, Long teamId, TeamDTO.TeamUpdateRequest request) {
        Long tenantId = requireTenantId(currentUser);
        String role = permissionService.activeRole(tenantId, teamId, currentUser.getUserId());
        if (!permissionService.canUpdateTeam(role)) {
            throw biz(ErrorCode.FORBIDDEN, "Team update requires owner, admin, or manager");
        }
        int updated = jdbcTemplate.update(
                """
                        update team
                        set team_name = ?,
                            team_type = ?,
                            avatar_url = ?,
                            description = ?,
                            visibility = ?,
                            join_mode = ?,
                            updated_by = ?,
                            updated_at = ?
                        where tenant_id = ?
                          and id = ?
                          and deleted = 0
                          and status = 'ACTIVE'
                        """,
                trimRequired(request.getTeamName(), "Team name is required"),
                normalizeEnum(request.getTeamType(), "GENERAL", TEAM_TYPES, "Invalid team type"),
                trimToNull(request.getAvatarUrl()),
                trimToNull(request.getDescription()),
                normalizeEnum(request.getVisibility(), "PRIVATE", VISIBILITIES, "Invalid team visibility"),
                normalizeEnum(request.getJoinMode(), "INVITE_ONLY", JOIN_MODES, "Invalid join mode"),
                currentUser.getUserId(),
                LocalDateTime.now(),
                tenantId,
                teamId
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        audit(currentUser, "team", "update", "UPDATE", "Updated team " + teamId);
        return getTeam(currentUser, teamId);
    }

    @Transactional
    public boolean deleteTeam(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        permissionService.requireTeamOwner(tenantId, teamId, currentUser.getUserId());
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                "update team set deleted = 1, status = 'DELETED', updated_by = ?, updated_at = ? where tenant_id = ? and id = ? and deleted = 0",
                currentUser.getUserId(),
                now,
                tenantId,
                teamId
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        jdbcTemplate.update("update team_member set deleted = 1, status = 'REMOVED', updated_at = ? where tenant_id = ? and team_id = ? and deleted = 0", now, tenantId, teamId);
        jdbcTemplate.update("update team_invite set deleted = 1, status = 'DISABLED', updated_at = ? where tenant_id = ? and team_id = ? and deleted = 0", now, tenantId, teamId);
        jdbcTemplate.update("update team_join_request set deleted = 1, status = 'CLOSED', updated_at = ? where tenant_id = ? and team_id = ? and deleted = 0", now, tenantId, teamId);
        audit(currentUser, "team", "delete", "DELETE", "Deleted team " + teamId);
        return true;
    }

    public List<TeamVO.Member> listMembers(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        permissionService.requireTeamMember(tenantId, teamId, currentUser.getUserId());
        return jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, team_id as teamId, user_id as userId, role,
                               member_alias as memberAlias, status, invited_by as invitedBy,
                               joined_at as joinedAt, created_at as createdAt
                        from team_member
                        where tenant_id = ?
                          and team_id = ?
                          and deleted = 0
                        order by field(role, 'OWNER', 'ADMIN', 'MANAGER', 'MEMBER'), id asc
                        """,
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                tenantId,
                teamId
        );
    }

    @Transactional
    public TeamVO.Member updateMemberRole(CurrentUser currentUser, Long teamId, Long memberId, TeamDTO.MemberRoleRequest request) {
        Long tenantId = requireTenantId(currentUser);
        String actorRole = permissionService.activeRole(tenantId, teamId, currentUser.getUserId());
        if (!TeamPermissionService.OWNER.equals(actorRole) && !TeamPermissionService.ADMIN.equals(actorRole)) {
            throw biz(ErrorCode.FORBIDDEN, "Member role updates require owner or admin");
        }
        TeamVO.Member target = requireMember(tenantId, teamId, memberId);
        String newRole = normalizeEnum(request.getRole(), null, MEMBER_ROLES, "Invalid team member role");
        if (TeamPermissionService.OWNER.equals(newRole)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Use owner transfer to assign OWNER");
        }
        if (TeamPermissionService.OWNER.equals(target.getRole())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Cannot downgrade the team owner directly");
        }
        jdbcTemplate.update(
                "update team_member set role = ?, updated_at = ? where tenant_id = ? and team_id = ? and id = ? and deleted = 0",
                newRole,
                LocalDateTime.now(),
                tenantId,
                teamId,
                memberId
        );
        return requireMember(tenantId, teamId, memberId);
    }

    @Transactional
    public boolean removeMember(CurrentUser currentUser, Long teamId, Long memberId) {
        Long tenantId = requireTenantId(currentUser);
        TeamVO.Member actor = permissionService.activeMember(tenantId, teamId, currentUser.getUserId());
        TeamVO.Member target = requireMember(tenantId, teamId, memberId);
        boolean self = target.getUserId().equals(currentUser.getUserId());
        if (!permissionService.canRemoveMember(actor == null ? null : actor.getRole(), target.getRole(), self)) {
            throw biz(ErrorCode.FORBIDDEN, "Cannot remove this team member");
        }
        jdbcTemplate.update(
                "update team_member set status = 'REMOVED', deleted = 1, updated_at = ? where tenant_id = ? and team_id = ? and id = ? and deleted = 0",
                LocalDateTime.now(),
                tenantId,
                teamId,
                memberId
        );
        refreshMemberCount(tenantId, teamId);
        return true;
    }

    @Transactional
    public boolean leaveTeam(CurrentUser currentUser, Long teamId) {
        Long tenantId = requireTenantId(currentUser);
        TeamVO.Member member = permissionService.activeMember(tenantId, teamId, currentUser.getUserId());
        if (member == null) {
            return true;
        }
        if (TeamPermissionService.OWNER.equals(member.getRole())) {
            throw biz(ErrorCode.BIZ_ERROR, "Owner must transfer ownership or disband the team before leaving");
        }
        return removeMember(currentUser, teamId, member.getId());
    }

    @Transactional
    public TeamVO.Team transferOwner(CurrentUser currentUser, Long teamId, TeamDTO.TransferOwnerRequest request) {
        Long tenantId = requireTenantId(currentUser);
        permissionService.requireTeamOwner(tenantId, teamId, currentUser.getUserId());
        TeamVO.Member target = requireMember(tenantId, teamId, request.getMemberId());
        if (!"ACTIVE".equals(target.getStatus())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "New owner must be an active member");
        }
        String previousRole = normalizeEnum(request.getPreviousOwnerRole(), "ADMIN", Set.of("ADMIN", "MANAGER", "MEMBER"), "Invalid previous owner role");
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "update team_member set role = ? where tenant_id = ? and team_id = ? and user_id = ? and status = 'ACTIVE' and deleted = 0",
                previousRole,
                tenantId,
                teamId,
                currentUser.getUserId()
        );
        jdbcTemplate.update("update team_member set role = 'OWNER', updated_at = ? where tenant_id = ? and team_id = ? and id = ?", now, tenantId, teamId, target.getId());
        jdbcTemplate.update("update team set owner_user_id = ?, updated_by = ?, updated_at = ? where tenant_id = ? and id = ? and deleted = 0", target.getUserId(), currentUser.getUserId(), now, tenantId, teamId);
        audit(currentUser, "team", "transferOwner", "UPDATE", "Transferred team owner " + teamId);
        return getTeam(currentUser, teamId);
    }

    TeamVO.Team queryTeam(Long tenantId, Long teamId, Long currentUserId) {
        List<TeamVO.Team> teams = jdbcTemplate.query(
                teamSelect("""
                        left join team_member m on m.team_id = t.id
                         and m.tenant_id = t.tenant_id
                         and m.user_id = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.tenant_id = ?
                          and t.id = ?
                          and t.deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                currentUserId,
                tenantId,
                teamId
        );
        return teams.isEmpty() ? null : teams.get(0);
    }

    TeamVO.Member requireMember(Long tenantId, Long teamId, Long memberId) {
        TeamVO.Member member = permissionService.memberById(tenantId, teamId, memberId);
        if (member == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team member not found");
        }
        return member;
    }

    void ensureDirectMember(Long tenantId, Long teamId, Long userId, Long invitedBy, String role) {
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
        refreshMemberCount(tenantId, teamId);
    }

    void refreshMemberCount(Long tenantId, Long teamId) {
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

    Long requireTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getCurrentTenantId();
    }

    Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null || currentUser.getUserId() <= 0) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private String nextTeamCode(Long tenantId) {
        for (int i = 0; i < 10; i += 1) {
            byte[] bytes = new byte[6];
            SECURE_RANDOM.nextBytes(bytes);
            String code = "T" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
            if (!jdbcTemplate.exists("select 1 from team where tenant_id = ? and team_code = ? and deleted = 0 limit 1", tenantId, code)) {
                return code;
            }
        }
        throw biz(ErrorCode.SYSTEM_ERROR, "Unable to allocate team code");
    }

    private String teamSelect(String tail) {
        return """
                select t.id, t.tenant_id as tenantId, t.team_code as teamCode, t.team_name as teamName,
                       t.team_type as teamType, t.avatar_url as avatarUrl, t.description,
                       t.visibility, t.join_mode as joinMode, t.owner_user_id as ownerUserId,
                       t.member_count as memberCount, t.status, m.role as myRole,
                       t.created_at as createdAt, t.updated_at as updatedAt
                from team t
                """ + tail;
    }

    String normalizeEnum(String value, String defaultValue, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (normalized == null || !allowed.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void audit(CurrentUser currentUser, String module, String action, String type, String message) {
        if (operationAuditService != null) {
            operationAuditService.log(currentUser.getCurrentTenantId(), currentUser.getUserId(), currentUser.getUsername(), module, action, type, "SUCCESS", message);
        }
    }

    static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
