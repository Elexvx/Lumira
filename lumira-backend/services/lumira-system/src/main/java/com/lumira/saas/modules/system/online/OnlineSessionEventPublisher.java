package com.lumira.saas.modules.system.online;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OnlineSessionEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public OnlineSessionEventPublisher(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(OnlineSessionEvent event) {
        try {
            OnlineSessionEventTrustValidator.requireTrustedEvent(event);
            String payload = objectMapper.writeValueAsString(event);
            OnlineSessionEventTrustValidator.requireTrustedSerializedEvent(payload);
            stringRedisTemplate.convertAndSend(CacheKeyConstants.onlineSessionEventsChannel(), payload);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "在线会话事件序列化失败");
        }
    }
}
