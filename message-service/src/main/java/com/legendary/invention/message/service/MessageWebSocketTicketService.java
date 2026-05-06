package com.legendary.invention.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.message.infrastructure.redis.CacheTemplate;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.message.config.MessageProperties;
import com.legendary.invention.message.vo.MessageVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class MessageWebSocketTicketService {

    public static final long TICKET_EXPIRES_IN_SECONDS = 30;

    private static final String CACHE_KEY_PREFIX = "message:ws-ticket:";

    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;

    public MessageWebSocketTicketService(CacheTemplate cacheTemplate, ObjectMapper objectMapper, MessageProperties messageProperties) {
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
        this.messageProperties = messageProperties;
    }

    public MessageVO.WebSocketTicketVO issue(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.isAuthenticated() || currentUser.getSessionId() == null || currentUser.getSessionVersion() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少有效会话");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        cacheTemplate.put(buildCacheKey(ticket), serializePayload(new TicketPayload(
                currentUser.getSessionId(),
                currentUser.getUserId(),
                currentUser.getSessionVersion()
        )), Duration.ofSeconds(messageProperties.getWsTicketExpiresInSeconds()));

        MessageVO.WebSocketTicketVO response = new MessageVO.WebSocketTicketVO();
        response.setTicket(ticket);
        response.setExpiresInSeconds(messageProperties.getWsTicketExpiresInSeconds());
        return response;
    }

    public TicketPayload consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String payload = cacheTemplate.getAndRemove(buildCacheKey(ticket.trim()));
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, TicketPayload.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "WebSocket凭证无效");
        }
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

    public record TicketPayload(String sessionId, Long userId, Integer sessionVersion) {
    }
}
