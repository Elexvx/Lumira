package com.lumira.team.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamInviteRepository;
import com.lumira.team.repository.TeamJoinRequestRepository;
import com.lumira.team.vo.TeamVO;
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
    private static final char[] INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final TeamAppService teamAppService;
    private final TeamPermissionService permissionService;
    private final TeamInviteRepository teamInviteRepository;
    private final TeamJoinRequestRepository teamJoinRequestRepository;
    private final TeamAuditPort auditPort;

    public TeamInviteService(
            TeamAppService teamAppService,
            TeamPermissionService permissionService,
            TeamInviteRepository teamInviteRepository,
            TeamJoinRequestRepository teamJoinRequestRepository,
            TeamAuditPort auditPort
    ) {
        this.teamAppService = teamAppService;
        this.permissionService = permissionService;
        this.teamInviteRepository = teamInviteRepository;
        this.teamJoinRequestRepository = teamJoinRequestRepository;
        this.auditPort = auditPort;
    }

    @Transactional
    public TeamVO.Invite createInvite(CurrentUser currentUser, Long teamId, TeamDTO.InviteCreateRequest request) {
        Long userId = teamAppService.requireUserId(currentUser);
        String actorRole = permissionService.activeRole(teamId, userId);
        if (!permissionService.canInvite(actorRole)) {
            throw TeamAppService.biz(ErrorCode.FORBIDDEN, "Invite creation requires owner or admin");
        }
        TeamVO.Team team = requireActiveTeam(teamId, userId);
        String rawToken = generateRawToken();
        String hash = sha256(rawToken);
        String inviteCode = allocateInviteCode(request.getInviteCode());
        Long inviteId = teamInviteRepository.createInvite(
                teamId,
                inviteCode,
                hash,
                normalizeText(request.getInviteType(), "LINK"),
                teamAppService.normalizeEnum(request.getRoleOnJoin(), "MEMBER", ROLES_ON_JOIN, "Invalid role on join"),
                request.getExpiresAt(),
                normalizeMaxUses(request.getMaxUses()),
                Boolean.TRUE.equals(request.getNeedApproval()),
                userId
        );
        TeamVO.Invite invite = requireInvite(teamId, inviteId);
        invite.setRawToken(rawToken);
        invite.setInviteUrl("/team/join?token=" + rawToken);
        audit(currentUser, "teamInvite", "create", "CREATE", "Created invite for " + team.getTeamName());
        return invite;
    }

    public List<TeamVO.Invite> listInvites(CurrentUser currentUser, Long teamId) {
        Long userId = teamAppService.requireUserId(currentUser);
        permissionService.requireTeamAdmin(teamId, userId);
        return teamInviteRepository.listInvites(teamId);
    }

    @Transactional
    public boolean disableInvite(CurrentUser currentUser, Long teamId, Long inviteId) {
        Long userId = teamAppService.requireUserId(currentUser);
        permissionService.requireTeamAdmin(teamId, userId);
        if (!teamInviteRepository.disableInvite(teamId, inviteId)) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Invite not found");
        }
        return true;
    }

    public TeamVO.InvitePreview previewByToken(String rawToken) {
        TeamVO.Invite invite = queryInviteByToken(rawToken);
        validateInviteUsable(invite);
        TeamVO.Team team = requireActiveTeam(invite.getTeamId(), null);
        TeamVO.InvitePreview preview = new TeamVO.InvitePreview();
        preview.setTeamName(team.getTeamName());
        preview.setTeamType(team.getTeamType());
        preview.setVisibility(team.getVisibility());
        preview.setInviteStatus(invite.getStatus());
        preview.setNeedApproval(invite.getNeedApproval());
        preview.setExpiresAt(invite.getExpiresAt());
        return preview;
    }

    @Transactional
    public TeamVO.JoinResult joinByToken(CurrentUser currentUser, String rawToken) {
        TeamVO.Invite invite = queryInviteByToken(rawToken);
        validateInviteUsable(invite);
        return joinWithInvite(currentUser, invite);
    }

    @Transactional
    public TeamVO.JoinResult joinByCode(CurrentUser currentUser, String inviteCode) {
        teamAppService.requireUserId(currentUser);
        TeamVO.Invite invite = teamInviteRepository.findByCode(normalizeInviteCode(inviteCode));
        if (invite == null) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Invite not found");
        }
        validateInviteUsable(invite);
        return joinWithInvite(currentUser, invite);
    }

    @Transactional
    public TeamVO.JoinResult createJoinRequest(CurrentUser currentUser, Long teamId, TeamDTO.JoinRequestCreateRequest request) {
        Long userId = teamAppService.requireUserId(currentUser);
        TeamVO.Team team = requireActiveTeam(teamId, userId);
        if ("PRIVATE".equals(team.getVisibility()) && !"APPLY".equals(team.getJoinMode()) && !"OPEN".equals(team.getJoinMode())) {
            throw TeamAppService.biz(ErrorCode.FORBIDDEN, "This team does not accept join requests");
        }
        if (permissionService.activeMember(teamId, userId) != null) {
            return joinedResult(team);
        }
        return pendingResult(createPendingRequest(teamId, userId, null, request == null ? null : request.getApplyMessage()), team);
    }

    public List<TeamVO.JoinRequest> listJoinRequests(CurrentUser currentUser, Long teamId) {
        Long userId = teamAppService.requireUserId(currentUser);
        permissionService.requireTeamAdmin(teamId, userId);
        return teamJoinRequestRepository.listByTeam(teamId);
    }

    @Transactional
    public TeamVO.JoinRequest approveJoinRequest(CurrentUser currentUser, Long teamId, Long requestId, TeamDTO.JoinReviewRequest request) {
        Long userId = teamAppService.requireUserId(currentUser);
        permissionService.requireTeamAdmin(teamId, userId);
        TeamVO.JoinRequest joinRequest = requireJoinRequest(teamId, requestId);
        if (!"PENDING".equals(joinRequest.getStatus())) {
            throw TeamAppService.biz(ErrorCode.BIZ_ERROR, "Join request already reviewed");
        }
        teamAppService.ensureDirectMember(teamId, joinRequest.getUserId(), userId, "MEMBER");
        String reviewMessage = request == null ? null : teamAppService.trimToNull(request.getReviewMessage());
        if (!teamJoinRequestRepository.approve(teamId, requestId, userId, reviewMessage)) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Pending join request not found");
        }
        return requireJoinRequest(teamId, requestId);
    }

    @Transactional
    public TeamVO.JoinRequest rejectJoinRequest(CurrentUser currentUser, Long teamId, Long requestId, TeamDTO.JoinReviewRequest request) {
        Long userId = teamAppService.requireUserId(currentUser);
        permissionService.requireTeamAdmin(teamId, userId);
        String reviewMessage = request == null ? null : teamAppService.trimToNull(request.getReviewMessage());
        if (!teamJoinRequestRepository.reject(teamId, requestId, userId, reviewMessage)) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Pending join request not found");
        }
        return requireJoinRequest(teamId, requestId);
    }

    private TeamVO.JoinResult joinWithInvite(CurrentUser currentUser, TeamVO.Invite invite) {
        Long userId = teamAppService.requireUserId(currentUser);
        TeamVO.Team team = requireActiveTeam(invite.getTeamId(), userId);
        if (permissionService.activeMember(invite.getTeamId(), userId) != null) {
            return joinedResult(team);
        }
        if (Boolean.TRUE.equals(invite.getNeedApproval()) || "APPLY".equals(team.getJoinMode())) {
            TeamVO.JoinRequest pendingRequest = teamJoinRequestRepository.findPending(invite.getTeamId(), userId);
            if (pendingRequest == null) {
                consumeInvite(invite);
                pendingRequest = createPendingRequest(invite.getTeamId(), userId, invite.getId(), null);
            }
            return pendingResult(pendingRequest, team);
        }
        consumeInvite(invite);
        teamAppService.ensureDirectMember(invite.getTeamId(), userId, null, invite.getRoleOnJoin());
        return joinedResult(requireActiveTeam(invite.getTeamId(), userId));
    }

    private TeamVO.JoinRequest createPendingRequest(Long teamId, Long userId, Long inviteId, String applyMessage) {
        try {
            Long id = teamJoinRequestRepository.createPending(teamId, userId, inviteId, teamAppService.trimToNull(applyMessage));
            return requireJoinRequest(teamId, id);
        } catch (DuplicateKeyException exception) {
            TeamVO.JoinRequest existing = teamJoinRequestRepository.findPending(teamId, userId);
            if (existing != null) {
                return existing;
            }
            throw exception;
        }
    }

    private void consumeInvite(TeamVO.Invite invite) {
        if (!teamInviteRepository.consumeInviteQuota(invite)) {
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
        requireActiveTeam(invite.getTeamId(), null);
    }

    private TeamVO.Invite queryInviteByToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw TeamAppService.biz(ErrorCode.VALIDATION_ERROR, "Invite token is required");
        }
        return teamInviteRepository.findByTokenHash(sha256(rawToken));
    }

    private TeamVO.Invite requireInvite(Long teamId, Long inviteId) {
        TeamVO.Invite invite = teamInviteRepository.findById(teamId, inviteId);
        if (invite == null) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Invite not found");
        }
        return invite;
    }

    private TeamVO.JoinRequest requireJoinRequest(Long teamId, Long requestId) {
        TeamVO.JoinRequest request = teamJoinRequestRepository.findById(teamId, requestId);
        if (request == null) {
            throw TeamAppService.biz(ErrorCode.NOT_FOUND, "Join request not found");
        }
        return request;
    }

    private TeamVO.Team requireActiveTeam(Long teamId, Long currentUserId) {
        TeamVO.Team team = teamAppService.queryTeam(teamId, currentUserId);
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

    private String allocateInviteCode(String requestedCode) {
        String normalized = normalizeInviteCode(requestedCode);
        if (normalized != null) {
            return normalized;
        }
        for (int attempt = 0; attempt < 20; attempt += 1) {
            String generated = generateInviteCode();
            if (!teamInviteRepository.existsActiveCode(generated)) {
                return generated;
            }
        }
        throw TeamAppService.biz(ErrorCode.SYSTEM_ERROR, "Unable to allocate invite code");
    }

    private String generateInviteCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i += 1) {
            code.append(INVITE_CODE_ALPHABET[SECURE_RANDOM.nextInt(INVITE_CODE_ALPHABET.length)]);
        }
        return code.toString();
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

    private void audit(CurrentUser currentUser, String module, String action, String type, String message) {
        if (auditPort != null) {
            auditPort.log(currentUser.getUserId(), currentUser.getUsername(), module, action, type, "SUCCESS", message);
        }
    }
}
