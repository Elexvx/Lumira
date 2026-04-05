package com.yourcompany.saas.modules.system.online;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.constant.CacheKeyConstants;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
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
            stringRedisTemplate.convertAndSend(CacheKeyConstants.onlineSessionEventsChannel(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "在线会话事件序列化失败");
        }
    }
}
