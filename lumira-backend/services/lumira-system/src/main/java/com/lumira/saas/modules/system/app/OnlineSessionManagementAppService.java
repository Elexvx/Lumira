package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class OnlineSessionManagementAppService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_PAGE_NO = 100_000L;
    private static final int MAX_SESSION_SCAN_SIZE = 10_000;
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final long PROTECTED_ADMIN_ID = 1001L;
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");
    private static final String PERMISSION_VIEW = "system:online-user:view";
    private static final String PERMISSION_KICK = "system:online-user:kick";
    private static final String PERMISSION_BAN = "system:online-user:ban";
    private static final String STATUS_ENABLED = "ENABLED";

    private final MyBatisQueryOperations jdbcTemplate;
    private final AuthSessionStore authSessionStore;
    private final SecuritySettingsService securitySettingsService;
    private final OperationAuditService operationAuditService;
    private final OnlineSessionStreamService onlineSessionStreamService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public OnlineSessionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            AuthSessionStore authSessionStore,
            SecuritySettingsService securitySettingsService,
            OperationAuditService operationAuditService,
            OnlineSessionStreamService onlineSessionStreamService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                jdbcTemplate,
                authSessionStore,
                securitySettingsService,
                operationAuditService,
                onlineSessionStreamService,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    @Autowired
    public OnlineSessionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            AuthSessionStore authSessionStore,
            SecuritySettingsService securitySettingsService,
            OperationAuditService operationAuditService,
            OnlineSessionStreamService onlineSessionStreamService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, authSessionStore, securitySettingsService, operationAuditService, onlineSessionStreamService, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private OnlineSessionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            AuthSessionStore authSessionStore,
            SecuritySettingsService securitySettingsService,
            OperationAuditService operationAuditService,
            OnlineSessionStreamService onlineSessionStreamService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authSessionStore = authSessionStore;
        this.securitySettingsService = securitySettingsService;
        this.operationAuditService = operationAuditService;
        this.onlineSessionStreamService = onlineSessionStreamService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public OnlineSessionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            AuthSessionStore authSessionStore,
            SecuritySettingsService securitySettingsService,
            OperationAuditService operationAuditService,
            OnlineSessionStreamService onlineSessionStreamService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                authSessionStore,
                securitySettingsService,
                operationAuditService,
                onlineSessionStreamService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                false
        );
    }

    public OnlineSessionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            AuthSessionStore authSessionStore,
            SecuritySettingsService securitySettingsService,
            OperationAuditService operationAuditService,
            OnlineSessionStreamService onlineSessionStreamService
    ) {
        this(jdbcTemplate, authSessionStore, securitySettingsService, operationAuditService, onlineSessionStreamService, null, null, null, false);
    }

    public PageResponse<SystemVO.OnlineSessionVO> listOnlineSessions(CurrentUser currentUser, long pageNo, long pageSize) {
        requirePermission(currentUser, PERMISSION_VIEW);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        long idleTimeoutSeconds = securitySettingsService.getIdleTimeoutSeconds();

        List<AuthSession> sessions = fetchOnlineSessions(idleTimeoutSeconds);
        long total = sessions.size();
        long start = (normalizedPageNo - 1) * normalizedPageSize;
        if (start >= total) {
            sessions = List.of();
        } else {
            long end = Math.min(total, start + normalizedPageSize);
            sessions = new ArrayList<>(sessions.subList((int) start, (int) end));
        }
        Map<Long, UserRow> userMap = loadUsers(sessions.stream().map(AuthSession::getUserId).filter(Objects::nonNull).toList());

        PageResponse<SystemVO.OnlineSessionVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total);
        response.setRecords(sessions.stream().map(session -> toOnlineSessionVO(session, userMap.get(session.getUserId()))).toList());
        return response;
    }

    public boolean kickSession(CurrentUser currentUser, String sessionId) {
        Long operatorId = requirePermission(currentUser, PERMISSION_KICK);
        String normalizedSessionId = requireText(sessionId, "Session id", MAX_SESSION_ID_LENGTH);
        AuthSession session = authSessionStore.findBySessionId(normalizedSessionId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Online session does not exist or has expired"));

        if (normalizedSessionId.equals(currentUser.getSessionId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "You cannot kick your current online session");
        }

        authSessionStore.remove(session, true);
        operationAuditService.log(operatorId,
                currentUser.getUserUuid(),
                trustedUsername(currentUser),
                "online-user",
                "kick",
                "KICK",
                "SUCCESS",
                "Kick online session: " + session.getUsername() + " / " + normalizedSessionId
        );
        return true;
    }

    @Transactional
    public boolean banUser(CurrentUser currentUser, Long userId) {
        Long operatorId = requirePermission(currentUser, PERMISSION_BAN);
        requirePositiveId(userId, "User id");
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "User id is required");
        }
        if (userId.equals(operatorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "You cannot ban your own account");
        }

        UserRow targetUser = loadUser(userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "User does not exist or has been deleted"));

        if (isProtectedAdminAccount(targetUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Protected admin account cannot be banned");
        }

        int updated = jdbcTemplate.update(
                "update sys_user set status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                operatorId,
                currentUser.getUserUuid(),
                LocalDateTime.now(),
                userId,
                targetUser.getUuid()
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "User changed, please retry");
        }
        authSessionStore.revokeUserSessions(userId, targetUser.getUuid(), true);
        operationAuditService.log(operatorId,
                currentUser.getUserUuid(),
                trustedUsername(currentUser),
                "online-user",
                "ban",
                "BAN",
                "SUCCESS",
                "强制下线并禁止在线会话: " + targetUser.getUsername() + " / " + userId
        );
        return true;
    }

    public void revokeUserSessions(Long userId, String userUuid) {
        requirePositiveId(userId, "User id");
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "User uuid is required");
        }
        String trustedUserUuid = loadUser(userId)
                .map(UserRow::getUuid)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "User does not exist or cannot be accessed"));
        if (!trustedUserUuid.trim().equals(userUuid.trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        authSessionStore.revokeUserSessions(userId, trustedUserUuid.trim(), true);
    }

    public void retainLatestSessionForEachUser() {
        authSessionStore.retainLatestSessionForEachUser();
    }

    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_VIEW);
        return onlineSessionStreamService.openStream(currentUser);
    }

    private List<AuthSession> fetchOnlineSessions(long idleTimeoutSeconds) {
        List<String> sessionIds = authSessionStore.listActiveSessionIds(0, MAX_SESSION_SCAN_SIZE - 1L);
        if (CollectionUtils.isEmpty(sessionIds)) {
            return List.of();
        }

        Map<String, AuthSession> sessionMap = authSessionStore.findBySessionIds(sessionIds);
        List<AuthSession> collected = new ArrayList<>();
        for (String sessionId : sessionIds) {
            AuthSession session = sessionMap.get(sessionId);
            if (session != null) {
                if (isOnlineSession(session, idleTimeoutSeconds)) {
                    collected.add(session);
                } else {
                    authSessionStore.remove(session, true);
                }
            } else {
                authSessionStore.removeSessionReferences(sessionId);
            }
        }
        if (securitySettingsService.isAllowMultiDeviceLogin()) {
            return collected;
        }

        Map<SessionOwnerKey, String> latestSessionIds = loadLatestSessionIdsByUser(collected);
        List<AuthSession> latestSessions = new ArrayList<>();
        for (AuthSession session : collected) {
            SessionOwnerKey ownerKey = SessionOwnerKey.from(session);
            if (ownerKey == null) {
                continue;
            }
            String latestSessionId = latestSessionIds.get(ownerKey);
            if (session.getSessionId().equals(latestSessionId)) {
                latestSessions.add(session);
            }
        }
        return latestSessions;
    }

    private Map<SessionOwnerKey, String> loadLatestSessionIdsByUser(List<AuthSession> sessions) {
        Set<SessionOwnerKey> userIds = new LinkedHashSet<>();
        Map<SessionOwnerKey, String> latestSessionIds = new LinkedHashMap<>();
        for (AuthSession session : sessions) {
            SessionOwnerKey ownerKey = SessionOwnerKey.from(session);
            if (ownerKey != null) {
                userIds.add(ownerKey);
                latestSessionIds.putIfAbsent(ownerKey, session.getSessionId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return latestSessionIds;
    }

    private boolean isOnlineSession(AuthSession session, long idleTimeoutSeconds) {
        if (session == null || session.getExpireTime() == null || !session.getExpireTime().isAfter(Instant.now())) {
            return false;
        }

        if (idleTimeoutSeconds <= 0) {
            return true;
        }

        Instant lastActivityAt = session.getLastActivityAt() != null ? session.getLastActivityAt() : session.getLoginTime();
        if (lastActivityAt == null) {
            return false;
        }

        Duration idleDuration = Duration.between(lastActivityAt, Instant.now());
        return idleDuration.compareTo(Duration.ofSeconds(idleTimeoutSeconds)) < 0;
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        Set<String> permissions = currentUser.getPermissions();
        if (permissions == null || permissions.isEmpty() || (!permissions.contains("*") && !permissions.contains(permissionKey))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return currentUser.getUserId();
    }

    private String trustedUsername(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUsername();
    }

    private String requireText(String value, String name, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is invalid");
        }
        return normalized;
    }

    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " must be a positive number");
        }
    }

    private long normalizePageNo(long pageNo) {
        if (pageNo < 1L || pageNo > MAX_PAGE_NO) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Page number is invalid");
        }
        return pageNo;
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Page size is invalid");
        }
        return pageSize;
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(currentUsername);
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        return authenticatedAccess.currentUser();
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private Map<Long, UserRow> loadUsers(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }

        List<Long> distinctIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = distinctIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<UserRow> rows = jdbcTemplate.query(
                """
                        select id, uuid, username, nickname, real_name as realName
                        from sys_user
                        where deleted = 0 and id in (%s)
                        """.formatted(placeholders),
                new BeanPropertyRowMapper<>(UserRow.class),
                distinctIds.toArray()
        );
        Map<Long, UserRow> result = new LinkedHashMap<>();
        for (UserRow row : rows) {
            result.put(row.getId(), row);
        }
        return result;
    }

    private java.util.Optional<UserRow> loadUser(Long userId) {
        List<UserRow> rows = jdbcTemplate.query(
                """
                        select u.id, u.uuid, u.username, u.nickname, u.real_name as realName
                        from sys_user u
                        where u.id = ? and u.deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(UserRow.class),
                userId
        );
        return rows.stream().findFirst();
    }

    private boolean isProtectedAdminAccount(UserRow user) {
        return user != null
                && Objects.equals(user.getId(), PROTECTED_ADMIN_ID);
    }

    private SystemVO.OnlineSessionVO toOnlineSessionVO(AuthSession session, UserRow userRow) {
        SystemVO.OnlineSessionVO vo = new SystemVO.OnlineSessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setUserId(session.getUserId());
        vo.setUserUuid(session.getUserUuid() != null ? session.getUserUuid() : (userRow == null ? null : userRow.getUuid()));
        vo.setUsername(session.getUsername());
        vo.setNickname(userRow != null && userRow.getNickname() != null ? userRow.getNickname() : null);
        vo.setRealName(userRow != null && userRow.getRealName() != null ? userRow.getRealName() : null);
        vo.setLoginTime(toLocalDateTime(session.getLoginTime()));
        vo.setLastActivityAt(toLocalDateTime(session.getLastActivityAt()));
        vo.setExpireTime(toLocalDateTime(session.getExpireTime()));
        vo.setClientType(session.getClientType());
        vo.setLoginIp(session.getLoginIp());
        vo.setUserAgent(session.getUserAgent());
        return vo;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private void ensureUserContext(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is missing");
        }
    }

    public static class UserRow {
        private Long id;
        private String uuid;
        private String username;
        private String nickname;
        private String realName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }
    }

    private record SessionOwnerKey(Long userId, String userUuid) {
        private static SessionOwnerKey from(AuthSession session) {
            if (session == null
                    || session.getUserId() == null
                    || session.getUserId() <= 0
                    || !StringUtils.hasText(session.getUserUuid())) {
                return null;
            }
            return new SessionOwnerKey(session.getUserId(), session.getUserUuid().trim());
        }
    }
}
