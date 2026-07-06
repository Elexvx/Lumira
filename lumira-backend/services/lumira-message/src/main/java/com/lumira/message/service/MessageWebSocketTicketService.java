package com.lumira.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.vo.MessageVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MessageWebSocketTicketService {

    public static final long TICKET_EXPIRES_IN_SECONDS = 30;

    private static final String CACHE_KEY_PREFIX = "message:ws-ticket:";
    private static final Pattern TICKET_PATTERN = Pattern.compile("[a-fA-F0-9]{32}");
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final int MAX_PAYLOAD_LENGTH = 4096;
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;
    private final MessageSessionAuthenticationService sessionAuthenticationService;

    public MessageWebSocketTicketService(
            CacheTemplate cacheTemplate,
            ObjectMapper objectMapper,
            MessageProperties messageProperties,
            MessageSessionAuthenticationService sessionAuthenticationService
    ) {
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
        this.messageProperties = messageProperties;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public MessageVO.WebSocketTicketVO issue(CurrentUser currentUser) {
        CurrentUser trustedCurrentUser = requireTrustedCurrentUser(currentUser);
        requireAnyPermission(trustedCurrentUser, "message:message:view", "system:notification:view");
        String trustedSessionId = normalizeSessionId(trustedCurrentUser.getSessionId());
        Long trustedUserId = trustedCurrentUser.getUserId();
        Integer trustedSessionVersion = trustedCurrentUser.getSessionVersion();
        if (trustedSessionId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Missing valid session");
        }
        long expiresInSeconds = messageProperties.getWsTicketExpiresInSeconds();
        if (expiresInSeconds <= 0 || expiresInSeconds > 300) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "WebSocket ticket TTL is invalid");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        cacheTemplate.put(buildCacheKey(ticket), serializePayload(new TicketPayload(
                trustedSessionId,
                trustedUserId,
                trustedCurrentUser.getUserUuid().trim(),
                normalizeSimulatedRoleId(trustedCurrentUser.getSimulatedRoleId()),
                trustedSessionVersion,
                trustedCurrentUser.getPermissionsVersion().trim()
        )), Duration.ofSeconds(expiresInSeconds));

        MessageVO.WebSocketTicketVO response = new MessageVO.WebSocketTicketVO();
        response.setTicket(ticket);
        response.setExpiresInSeconds(expiresInSeconds);
        return response;
    }

    private CurrentUser requireTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Missing valid session");
        }
        String trustedSessionId = normalizeSessionId(currentUser.getSessionId());
        if (trustedSessionId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Missing valid session");
        }
        MessageSessionAuthenticationService.AuthenticatedAccess authenticatedAccess = sessionAuthenticationService.authenticateSessionTicket(
                trustedSessionId,
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()),
                currentUser.getSessionVersion(),
                currentUser.getPermissionsVersion()
        );
        CurrentUser trustedCurrentUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(trustedCurrentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Missing valid session");
        }
        return trustedCurrentUser;
    }

    private void requireAnyPermission(CurrentUser currentUser, String... permissionKeys) {
        if (currentUser == null || currentUser.getPermissions() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + String.join(" or ", permissionKeys));
        }
        if (currentUser.getPermissions().contains("*")) {
            return;
        }
        for (String permissionKey : permissionKeys) {
            if (currentUser.getPermissions().contains(permissionKey)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + String.join(" or ", permissionKeys));
    }

    public TicketPayload consume(String ticket) {
        String normalizedTicket = normalizeTicket(ticket);
        if (normalizedTicket == null) {
            return null;
        }
        String payload = cacheTemplate.getAndRemove(buildCacheKey(normalizedTicket));
        if (payload == null || payload.isBlank()) {
            return null;
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            return null;
        }
        try {
            TicketPayload ticketPayload = objectMapper.readValue(payload, TicketPayload.class);
            return isValidPayload(ticketPayload) ? ticketPayload : null;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "WebSocket ticket is invalid");
        }
    }

    private String normalizeTicket(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        String normalized = ticket.trim();
        return TICKET_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private boolean isValidPayload(TicketPayload payload) {
        return payload != null
                && normalizeSessionId(payload.sessionId()) != null
                && payload.userId() != null
                && payload.userId() > 0
                && StringUtils.hasText(payload.userUuid())
                && isTrustedSimulatedRoleId(payload.simulatedRoleId())
                && payload.sessionVersion() != null
                && payload.sessionVersion() > 0
                && StringUtils.hasText(payload.permissionsVersion());
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private boolean isTrustedSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId > 0;
    }

    private String normalizeSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String normalized = sessionId.trim();
        if (normalized.length() > MAX_SESSION_ID_LENGTH
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            return null;
        }
        return normalized;
    }

    private String buildCacheKey(String ticket) {
        return CACHE_KEY_PREFIX + ticket;
    }

    private String serializePayload(TicketPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "WebSocket凭证生成失败");
        }
    }

    public record TicketPayload(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
    }
}
