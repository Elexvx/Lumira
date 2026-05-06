package com.legendary.invention.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.message.vo.MessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageWebSocketRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MessageWebSocketRegistry.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> subscriberIdsByTenantId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> subscriberIdsByUserId = new ConcurrentHashMap<>();

    public MessageWebSocketRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session, Long tenantId, Long userId) {
        String subscriberId = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(subscriberId, session, tenantId, userId);
        subscribers.put(subscriberId, subscriber);
        subscriberIdsByTenantId.computeIfAbsent(tenantId, key -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        subscriberIdsByUserId.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        sendSafely(subscriber, buildEvent("CONNECTED", tenantId, userId, null, "消息通道已连接"));
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }

        Collection<String> subscriberIds = new ArrayList<>();
        for (Map.Entry<String, Subscriber> entry : subscribers.entrySet()) {
            if (entry.getValue().session().getId().equals(session.getId())) {
                subscriberIds.add(entry.getKey());
            }
        }
        subscriberIds.forEach(this::removeSubscriber);
    }

    public void sendToTenant(Long tenantId, MessageVO.MessageEventVO event) {
        dispatch(subscriberIdsByTenantId.get(tenantId), event, tenantId, null);
    }

    public void sendToUser(Long tenantId, Long userId, MessageVO.MessageEventVO event) {
        dispatch(subscriberIdsByUserId.get(userId), event, tenantId, userId);
    }

    public void sendHeartbeat() {
        for (Subscriber subscriber : subscribers.values()) {
            MessageVO.MessageEventVO heartbeat = buildEvent("HEARTBEAT", subscriber.tenantId(), subscriber.userId(), null, "heartbeat");
            sendSafely(subscriber, heartbeat);
        }
    }

    public void scheduledHeartbeat() {
        sendHeartbeat();
    }

    private void dispatch(Set<String> subscriberIds, MessageVO.MessageEventVO event, Long tenantId, Long userId) {
        if (subscriberIds == null || subscriberIds.isEmpty() || event == null) {
            return;
        }
        for (String subscriberId : subscriberIds) {
            Subscriber subscriber = subscribers.get(subscriberId);
            if (subscriber == null) {
                continue;
            }
            if (!subscriber.tenantId().equals(tenantId)) {
                continue;
            }
            if (userId != null && !subscriber.userId().equals(userId)) {
                continue;
            }
            sendSafely(subscriber, event);
        }
    }

    private void sendSafely(Subscriber subscriber, MessageVO.MessageEventVO event) {
        try {
            if (subscriber.session().isOpen()) {
                subscriber.session().sendMessage(new TextMessage(serialize(event)));
            } else {
                removeSubscriber(subscriber.subscriberId());
            }
        } catch (IOException | RuntimeException exception) {
            logger.debug("消息WebSocket发送失败: sessionId={}, message={}", subscriber.session().getId(), exception.getMessage());
            removeSubscriber(subscriber.subscriberId());
        }
    }

    private String serialize(MessageVO.MessageEventVO event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("消息事件序列化失败", exception);
        }
    }

    private void removeSubscriber(String subscriberId) {
        Subscriber subscriber = subscribers.remove(subscriberId);
        if (subscriber == null) {
            return;
        }

        Set<String> tenantSubscribers = subscriberIdsByTenantId.get(subscriber.tenantId());
        if (tenantSubscribers != null) {
            tenantSubscribers.remove(subscriberId);
            if (tenantSubscribers.isEmpty()) {
                subscriberIdsByTenantId.remove(subscriber.tenantId());
            }
        }

        Set<String> userSubscribers = subscriberIdsByUserId.get(subscriber.userId());
        if (userSubscribers != null) {
            userSubscribers.remove(subscriberId);
            if (userSubscribers.isEmpty()) {
                subscriberIdsByUserId.remove(subscriber.userId());
            }
        }
    }

    private MessageVO.MessageEventVO buildEvent(String eventType, Long tenantId, Long userId, MessageVO.NoticeVO notice, String message) {
        MessageVO.MessageEventVO event = new MessageVO.MessageEventVO();
        event.setEventType(eventType);
        event.setTenantId(tenantId);
        event.setUserId(userId);
        event.setNotice(notice);
        event.setMessage(message);
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    private record Subscriber(String subscriberId, WebSocketSession session, Long tenantId, Long userId) {
    }
}
