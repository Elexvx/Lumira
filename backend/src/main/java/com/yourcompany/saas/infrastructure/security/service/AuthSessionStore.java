package com.yourcompany.saas.infrastructure.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.constant.CacheKeyConstants;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.redis.CacheTemplate;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class AuthSessionStore {

    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;

    public AuthSessionStore(CacheTemplate cacheTemplate, ObjectMapper objectMapper) {
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(AuthSession session, Duration ttl) {
        try {
            cacheTemplate.put(CacheKeyConstants.sessionKey(session.getSessionId()), objectMapper.writeValueAsString(session), ttl);
            cacheTemplate.put(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()), "1", ttl);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话序列化失败");
        }
    }

    public Optional<AuthSession> findBySessionId(String sessionId) {
        String payload = cacheTemplate.get(CacheKeyConstants.sessionKey(sessionId));
        if (payload == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, AuthSession.class));
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "会话反序列化失败");
        }
    }

    public void remove(AuthSession session) {
        cacheTemplate.remove(CacheKeyConstants.sessionKey(session.getSessionId()));
        cacheTemplate.remove(CacheKeyConstants.userSessionKey(session.getUserId(), session.getSessionId()));
    }
}
