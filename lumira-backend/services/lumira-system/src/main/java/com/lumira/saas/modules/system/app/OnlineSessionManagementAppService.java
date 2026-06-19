package com.lumira.saas.modules.system.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
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
import java.util.stream.Collectors;

@Service
public class OnlineSessionManagementAppService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MyBatisQueryOperations jdbcTemplate;
    private final AuthSessionStore authSessionStore;
    private final SecuritySettingsService securitySettingsService;
    private final OperationAuditService operationAuditService;
    private final OnlineSessionStreamService onlineSessionStreamService;

    public OnlineSessionManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            AuthSessionStore authSessionStore,
            SecuritySettingsService securitySettingsService,
            OperationAuditService operationAuditService,
            OnlineSessionStreamService onlineSessionStreamService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authSessionStore = authSessionStore;
        this.securitySettingsService = securitySettingsService;
        this.operationAuditService = operationAuditService;
        this.onlineSessionStreamService = onlineSessionStreamService;
    }

    public PageResponse<SystemVO.OnlineSessionVO> listOnlineSessions(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        long normalizedPageNo = Math.max(pageNo, 1L);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long idleTimeoutSeconds = securitySettingsService.getIdleTimeoutSeconds();

        List<AuthSession> sessions = fetchOnlineTenantSessions(tenantId, idleTimeoutSeconds);
        long total = sessions.size();
        long start = (normalizedPageNo - 1) * normalizedPageSize;
        if (start >= total) {
            sessions = List.of();
        } else {
            long end = Math.min(total, start + normalizedPageSize);
            sessions = new ArrayList<>(sessions.subList((int) start, (int) end));
        }
        Map<Long, TenantUserRow> userMap = loadUsers(sessions.stream().map(AuthSession::getUserId).filter(Objects::nonNull).toList());

        PageResponse<SystemVO.OnlineSessionVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total);
        response.setRecords(sessions.stream().map(session -> toOnlineSessionVO(session, userMap.get(session.getUserId()))).toList());
        return response;
    }

    public boolean kickSession(CurrentUser currentUser, String sessionId) {
        Long tenantId = currentTenantId(currentUser);
        AuthSession session = authSessionStore.findBySessionId(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "在线会话不存在或已失效"));

        ensureTenantMatch(tenantId, session);
        if (sessionId.equals(currentUser.getSessionId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能踢出当前登录会话");
        }

        authSessionStore.remove(session, true);
        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "online-user",
                "kick",
                "KICK",
                "SUCCESS",
                "踢出在线会话: " + session.getUsername() + " / " + sessionId
        );
        return true;
    }

    @Transactional
    public boolean banUser(CurrentUser currentUser, Long userId) {
        Long tenantId = currentTenantId(currentUser);
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
        if (userId.equals(currentUser.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能封禁当前登录账号");
        }

        TenantUserRow targetUser = loadTenantUser(tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在或无权访问"));

        jdbcTemplate.update(
                "update sys_user set status = 'DISABLED', updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                currentUser.getUserId(),
                LocalDateTime.now(),
                userId
        );
        authSessionStore.revokeUserSessions(userId, true);
        operationAuditService.log(
                tenantId,
                currentUser.getUserId(),
                currentUser.getUsername(),
                "online-user",
                "ban",
                "BAN",
                "SUCCESS",
                "封禁账号并清退在线会话: " + targetUser.getUsername() + " / " + userId
        );
        return true;
    }

    public void revokeUserSessions(Long userId) {
        authSessionStore.revokeUserSessions(userId, true);
    }

    public void retainLatestSessionForEachUser() {
        authSessionStore.retainLatestSessionForEachUser();
    }

    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(CurrentUser currentUser) {
        return onlineSessionStreamService.openStream(currentUser);
    }

    private List<AuthSession> fetchOnlineTenantSessions(Long tenantId, long idleTimeoutSeconds) {
        List<String> sessionIds = authSessionStore.listActiveTenantSessionIds(tenantId, 0, -1);
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
                authSessionStore.removeTenantSessionReference(tenantId, sessionId);
            }
        }
        if (securitySettingsService.isAllowMultiDeviceLogin()) {
            return collected;
        }

        Map<Long, String> latestSessionIds = loadLatestSessionIdsByUser(collected);
        List<AuthSession> latestSessions = new ArrayList<>();
        for (AuthSession session : collected) {
            if (session.getUserId() == null) {
                continue;
            }
            String latestSessionId = latestSessionIds.get(session.getUserId());
            if (session.getSessionId().equals(latestSessionId)) {
                latestSessions.add(session);
            }
        }
        return latestSessions;
    }

    private Map<Long, String> loadLatestSessionIdsByUser(List<AuthSession> sessions) {
        Set<Long> userIds = new LinkedHashSet<>();
        Map<Long, String> latestSessionIds = new LinkedHashMap<>();
        for (AuthSession session : sessions) {
            if (session.getUserId() != null) {
                userIds.add(session.getUserId());
                latestSessionIds.putIfAbsent(session.getUserId(), session.getSessionId());
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

    private Map<Long, TenantUserRow> loadUsers(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }

        List<Long> distinctIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = distinctIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<TenantUserRow> rows = jdbcTemplate.query(
                """
                        select id, username, nickname, real_name as realName
                        from sys_user
                        where deleted = 0 and id in (%s)
                        """.formatted(placeholders),
                new BeanPropertyRowMapper<>(TenantUserRow.class),
                distinctIds.toArray()
        );
        Map<Long, TenantUserRow> result = new LinkedHashMap<>();
        for (TenantUserRow row : rows) {
            result.put(row.getId(), row);
        }
        return result;
    }

    private java.util.Optional<TenantUserRow> loadTenantUser(Long tenantId, Long userId) {
        List<TenantUserRow> rows = jdbcTemplate.query(
                """
                        select u.id, u.username, u.nickname, u.real_name as realName
                        from sys_user u
                        join sys_user_tenant ut
                          on ut.user_id = u.id
                         and ut.tenant_id = ?
                         and ut.deleted = 0
                        where u.id = ? and u.deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(TenantUserRow.class),
                tenantId,
                userId
        );
        return rows.stream().findFirst();
    }

    private SystemVO.OnlineSessionVO toOnlineSessionVO(AuthSession session, TenantUserRow userRow) {
        SystemVO.OnlineSessionVO vo = new SystemVO.OnlineSessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setUserId(session.getUserId());
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

    private Long currentTenantId(CurrentUser currentUser) {
        return com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }

    private void ensureTenantMatch(Long currentTenantId, AuthSession session) {
        if (!currentTenantId.equals(com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作当前平台会话");
        }
    }

    public static class TenantUserRow {
        private Long id;
        private String username;
        private String nickname;
        private String realName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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
}
